import com.github.lukethompsxn.edufuse.filesystem.FileSystemStub;
import com.github.lukethompsxn.edufuse.struct.*;
import com.github.lukethompsxn.edufuse.util.ErrorCodes;
import com.github.lukethompsxn.edufuse.util.FuseFillDir;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.types.dev_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import util.MemoryINode;
import util.MemoryINodeTable;
import util.MemoryVisualiser;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.sun.security.auth.module.UnixSystem;

/**
 * @author Luke Thompson and (add your name here)
 * @since 04.09.19
 */
public class MemoryFS extends FileSystemStub {
    private static final String HELLO_PATH = "/hello";
    private static final String HELLO_STR = "Hello World!\n";

    private MemoryINodeTable iNodeTable = new MemoryINodeTable();
    private MemoryVisualiser visualiser;
    private UnixSystem unix = new UnixSystem();

    @Override
    public Pointer init(Pointer conn) {

        // setup an example file for testing purposes
        MemoryINode iNode = new MemoryINode();
        FileStat stat = new FileStat(Runtime.getSystemRuntime());
        // you will have to add more stat information here eventually
        stat.st_mode.set(FileStat.S_IFREG | 0444 | 0200);
        stat.st_size.set(HELLO_STR.getBytes().length);
        stat.st_gid.set(unix.getGid());
        stat.st_uid.set(unix.getUid());
        stat.st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_atim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_nlink.set(1);
        iNode.setStat(stat);
        iNode.setContent(HELLO_STR.getBytes());
        iNodeTable.updateINode(HELLO_PATH, iNode);

        if (isVisualised()) {
            visualiser = new MemoryVisualiser();
            visualiser.sendINodeTable(iNodeTable);
        }

        return conn;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        int res = 0;
        if (Objects.equals(path, "/")) { // minimal set up for the mount point root
            stat.st_mode.set(FileStat.S_IFDIR | 0755);
            stat.st_nlink.set(2);
        } else if (iNodeTable.containsINode(path)) {
            FileStat savedStat = iNodeTable.getINode(path).getStat();
            // fill in the stat object with values from the savedStat object of your inode
            stat.st_mode.set(savedStat.st_mode.intValue());
            stat.st_size.set(savedStat.st_size.intValue());
            stat.st_gid.set(savedStat.st_gid.intValue());
            stat.st_uid.set(savedStat.st_uid.intValue());
            stat.st_blocks.set(savedStat.st_blocks.intValue());
            stat.st_ctim.tv_sec.set(savedStat.st_ctim.tv_sec.intValue());
            stat.st_ctim.tv_nsec.set(savedStat.st_ctim.tv_nsec.intValue());
            stat.st_atim.tv_sec.set(savedStat.st_atim.tv_sec.intValue());
            stat.st_atim.tv_nsec.set(savedStat.st_atim.tv_nsec.intValue());
            stat.st_mtim.tv_sec.set(savedStat.st_mtim.tv_sec.intValue());
            stat.st_mtim.tv_nsec.set(savedStat.st_mtim.tv_nsec.intValue());
            stat.st_nlink.set(savedStat.st_nlink.intValue());

        } else {
            res = -ErrorCodes.ENOENT();
        }
        return res;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
        // For each file in the directory call filler.apply.
        // The filler.apply method adds information on the files
        // in the directory, it has the following parameters:
        //      buf - a pointer to a buffer for the directory entries
        //      name - the file name (with no "/" at the beginning)
        //      stbuf - the FileStat information for the file
        //      off - just use 0
        filler.apply(buf, ".", null, 0);
        filler.apply(buf, "..", null, 0);
        for(String filename : iNodeTable.entries()) {
            File file = new File(filename);
            if (file.getParent().equals(path)) {
                filler.apply(buf, file.getName(), iNodeTable.getINode(filename).getStat(), 0);
            }
        }

        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        if (!iNodeTable.containsINode(path)) {
            return -ErrorCodes.ENOENT();
        }
        // you need to extract data from the content field of the inode and place it in the buffer
        // something like:
        // buf.put(0, content, offset, amount);
        MemoryINode iNode = iNodeTable.getINode(path);
        int amount = iNode.getContent().length;
        buf.put(offset,iNode.getContent(),0,amount);
        iNode.getStat().st_atim.tv_sec.set(System.currentTimeMillis()/1000);
        iNode.getStat().st_atim.tv_nsec.set(System.currentTimeMillis()*1000);

        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }

        return amount;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        if (!iNodeTable.containsINode(path)) {
            return -ErrorCodes.ENOENT(); // ENONET();
        }
        // similar to read but you get data from the buffer like:
        // buf.get(0, content, offset, size);@size
        MemoryINode iNode = iNodeTable.getINode(path);
        int len = iNode.getContent().length;
        byte[] content = new byte[(int)size];
        buf.get(0,content,0, (int)size);
        if (len < offset+size){
            byte[] newcontent = new byte[(int)offset+(int)size];
            System.arraycopy(iNode.getContent(), 0, newcontent, 0, len);
            System.arraycopy(content, 0, newcontent, (int)offset, (int)size);
            iNode.setContent(newcontent);
            iNode.getStat().st_size.set(newcontent.length);
            iNode.getStat().st_blocks.set(java.lang.Math.max(2,newcontent.length/512));
        } else {
            System.arraycopy(content, 0, iNode.getContent(), (int) offset, (int) size);
            iNode.getStat().st_blocks.set(java.lang.Math.max(2,iNode.getContent().length/512));
        }

        iNode.getStat().st_atim.tv_sec.set(System.currentTimeMillis()/1000);
        iNode.getStat().st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
        iNode.getStat().st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
        iNode.getStat().st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
        iNode.getStat().st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
        iNode.getStat().st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);

        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }

        return (int) size;
    }

    @Override
    public int mknod(String path, @mode_t long mode, @dev_t long rdev) {
        if (iNodeTable.containsINode(path)) {
            return -ErrorCodes.EEXIST();
        }
        File file = new File(path);
        if (file.getName().length()>255){
            return -ErrorCodes.ENAMETOOLONG();
        }
        File parentfile = file.getParentFile();
        if (parentfile.getParentFile() != null && parentfile.getAbsolutePath() != "/") {
            MemoryINode parentINode = iNodeTable.getINode(parentfile.getAbsolutePath());
            parentINode.getStat().st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_atim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);
        }
        MemoryINode mockINode = new MemoryINode();
        // set up the stat information for this inode
        FileStat stat = new FileStat(Runtime.getSystemRuntime());
        stat.st_mode.set(FileStat.S_IFREG | 0444 | 0200);
        stat.st_size.set(2048);
        stat.st_gid.set(unix.getGid());
        stat.st_uid.set(unix.getUid());
        stat.st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_atim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_nlink.set(1);
        mockINode.setStat(stat);
        iNodeTable.updateINode(path, mockINode);

        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }

        return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        return super.statfs(path, stbuf);
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        // The Timespec array has the following information.
        // You need to set the corresponding fields of the inode's stat object.
        // You can access the data in the Timespec objects with "get()" and "longValue()".
        // You have to find out which time fields these correspond to.
        // timespec[0].tv_nsec
        // timespec[0].tv_sec
        // timespec[1].tv_nsec
        // timespec[1].tv_sec
        return 0;
    }
    
    @Override
    public int link(java.lang.String oldpath, java.lang.String newpath) {
        if (!iNodeTable.containsINode(oldpath)) {
            return -ErrorCodes.ENONET();
        }

        MemoryINode iNode = iNodeTable.getINode(oldpath);
        iNode.getStat().st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
        iNode.getStat().st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
        iNode.getStat().st_nlink.set(iNode.getStat().st_nlink.intValue()+1);
        File file = new File(newpath);
        File parentfile = file.getParentFile();
        if (parentfile.getParentFile() != null && parentfile.getAbsolutePath() != "/") {
            MemoryINode parentINode = iNodeTable.getINode(parentfile.getAbsolutePath());
            parentINode.getStat().st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_atim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);
        }
        iNodeTable.updateINode(newpath,iNode);
        return 0;
    }

    @Override
    public int unlink(String path) {
        if (!iNodeTable.containsINode(path)) {
            return -ErrorCodes.ENONET();
        }
        // delete the file if there are no more hard links
        File file = new File(path);
        File parentfile = file.getParentFile();
        if (parentfile.getParentFile() != null && parentfile.getAbsolutePath() != "/") {
            MemoryINode parentINode = iNodeTable.getINode(parentfile.getAbsolutePath());
            parentINode.getStat().st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_atim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);
        }
        MemoryINode iNode = iNodeTable.getINode(path);
        iNode.getStat().st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
        iNode.getStat().st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
        int links = iNode.getStat().st_nlink.intValue();
        if(links > 1){
            iNode.getStat().st_nlink.set(links-1);
            iNodeTable.removeINode(path);
        } else{
            iNodeTable.removeINode(path);
        }
        return 0;
    }

    @Override
    public int mkdir(String path, long mode) {
        File newfile = new File(path);
        if (newfile.getName().length()>255){
            return -ErrorCodes.ENAMETOOLONG();
        }
        File parentfile = newfile.getParentFile();
        if (parentfile.getParentFile() != null && parentfile.getAbsolutePath() != "/") {
            MemoryINode parentINode = iNodeTable.getINode(parentfile.getAbsolutePath());
            parentINode.getStat().st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_atim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
            parentINode.getStat().st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);
            parentINode.getStat().st_nlink.set(parentINode.getStat().st_nlink.intValue()+1);

        }
        MemoryINode mockINode = new MemoryINode();
        // set up the stat information for this inode
        FileStat stat = new FileStat(Runtime.getSystemRuntime());
        stat.st_mode.set(FileStat.S_IFDIR);
        stat.st_size.set(0);
        stat.st_gid.set(unix.getGid());
        stat.st_uid.set(unix.getUid());
        stat.st_blocks.set(4);
        stat.st_ctim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_ctim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_atim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_atim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_mtim.tv_sec.set(System.currentTimeMillis()/1000);
        stat.st_mtim.tv_nsec.set(System.currentTimeMillis()*1000);
        stat.st_nlink.set(2);
        mockINode.setStat(stat);
        iNodeTable.updateINode(path, mockINode);

        if (isVisualised()) {
            visualiser.sendINodeTable(iNodeTable);
        }
        return 0;
    }

    @Override
    public int rmdir(String path) {
        MemoryINode iNode = iNodeTable.getINode(path);
        for(String filename : iNodeTable.entries()) {
            File file = new File(filename);
            if (file.getParent().equals(path)) {
                return -ErrorCodes.ENOTEMPTY();
            }
        }
        iNodeTable.removeINode(path);
        return 0;
    }

    @Override
    public int rename(String oldpath, String newpath) {
        return 0;
    }

    @Override
    public int truncate(String path, @size_t long size) {
        return 0;
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, @size_t long size, int flags) {
        return 0;
    }

    @Override
    public int getxattr(String path, String name, Pointer value, @size_t long size) {
        return 0;
    }

    @Override
    public int listxattr(String path, Pointer list, @size_t long size) {
        return 0;
    }

    @Override
    public int removexattr(String path, String name) {
        return 0;
    }

    @Override
    public int opendir(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int releasedir(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public void destroy(Pointer initResult) {
        if (isVisualised()) {
            try {
                visualiser.stopConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int access(String path, int mask) {
        return 0;
    }

    @Override
    public int lock(String path, FuseFileInfo fi, int cmd, Flock flock) {
        return 0;
    }

    public static void main(String[] args) {
        MemoryFS fs = new MemoryFS();
        try {
            fs.mount(args, true);
        } finally {
            fs.unmount();
        }
    }
}
