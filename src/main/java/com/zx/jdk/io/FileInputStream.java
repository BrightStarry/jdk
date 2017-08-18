package com.zx.jdk.io;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import sun.nio.ch.FileChannelImpl;
/**
 * 文件输入流
 * 他是用来读取原始的流的(byte),例如图像文件;
 * 如果需要读取大量字符，建议使用FileReader类
 */
public class FileInputStream extends InputStream
{
    //文件描述类，处理打开的文件
    private final FileDescriptor fd;

    /**
     * The path of the referenced file
     * (null if the stream is created with a file descriptor)
     */
    //引用文件的路径，如果该流是通过文件描述符创建的，该属性则为空
    private final String path;

    //用于读、写、映射、操作文件的通道
    private FileChannel channel = null;

    //关闭锁,该对象只在close()方法中使用，只是为了多个线程调用关闭方法的时候同步
    // 并将关闭方法和其他同步块区分开来,在其他同步块阻塞时，依然可以调用关闭方法
    private final Object closeLock = new Object();

    //是否是关闭的，默认为false，使用volatile关键字，确保其在线程间的可见性
    private volatile boolean closed = false;

    //通过文件名，或路径名创建该类，如果名字找不到或因其他原因无法读取，都会抛出异常
    public FileInputStream(String name) throws FileNotFoundException {
        //确保name不为空，然后通过name创建出File类，然后调用另一个重载的构造方法
        this(name != null ? new File(name) : null);
    }

   //本地方法，打开指定文件的读取
    private native void open0(String name) throws FileNotFoundException;

    /**
     * 使用File类创建一个文件输入流
     * 会使用安全管理器来检查File表示的目录或文件是否有 读权限
     */
    public FileInputStream(File file) throws FileNotFoundException {
        //获取File中的文件路径
        String name = (file != null ? file.getPath() : null);
        //获取当前系统的安全管理器对象
        SecurityManager security = System.getSecurityManager();
        //如果获取到了
        if (security != null) {
            //确保有该路径或文件的读取权限
            security.checkRead(name);
        }
        //如果 路径名 为空，抛异常
        if (name == null) {
            throw new NullPointerException();
        }
        //如果File类是无效的，抛异常
        if (file.isInvalid()) {
            throw new FileNotFoundException("Invalid file path");
        }
        //创建文件描述符
        fd = new FileDescriptor();
        //在文件描述符对象中保存 该对象的引用
        fd.attach(this);
        //将路径名 赋值给 path属性
        path = name;
        //打开该路径 文件或目录的 读取
        open(name);
    }

    /**
     * 使用文件描述符来创建该对象
     */
    public FileInputStream(FileDescriptor fdObj) {
        //获取 安全管理器
        SecurityManager security = System.getSecurityManager();
        //传入的 文件描述类如果为空，抛出异常
        if (fdObj == null) {
            throw new NullPointerException();
        }
        //如果安全管理器不为空，就检查该 文件的读取权限
        if (security != null) {
            security.checkRead(fdObj);
        }
        //赋值给自己的属性
        fd = fdObj;
        //使用文件描述符时，依赖的是文件描述符中的句柄，所以 路径为空
        path = null;

        /**
         * 文件描述符是可以被多个流引用的，所以它也要保存引用它的流的引用
         */
        fd.attach(this);
    }


    /**
     * Opens the specified file for reading.
     * @param name the name of the file
     */
    //打开指定文件的读取
    //包装了本地方法
    private void open(String name) throws FileNotFoundException {
        //本地方法，打开指定文件的读取
        open0(name);
    }

    //从流中读取下一个字节的数据，该方法会一直阻塞,知道有可用数据
    public int read() throws IOException {
        //调用本地方法
        return read0();
    }

    //本地方法，读取文件中下一字节的数据
    private native int read0() throws IOException;

    //本地方法，读取
    private native int readBytes(byte b[], int off, int len) throws IOException;

    //读取满b[]数组
    public int read(byte b[]) throws IOException {
        //调用本地方法
        return readBytes(b, 0, b.length);
    }

    //读取，包装本地方法
    public int read(byte b[], int off, int len) throws IOException {
        return readBytes(b, off, len);
    }

    //本地方法，跳过n个字节
    public native long skip(long n) throws IOException;

    //返回可用字节数，本地方法
    public native int available() throws IOException;

    //关闭方法
    public void close() throws IOException {
        //使用 上面的Object closeLock对象作为锁，确保此处的线程安全,并不受该类其他同步代码块的阻塞影响
        synchronized (closeLock) {
            //如果已经关闭，退出
            if (closed) {
                return;
            }
            //将变量设为 已经关闭
            closed = true;
        }
        //如果 操作文件的通道不为空，关闭
        if (channel != null) {
            channel.close();
        }
        //先关闭引用该文件描述符的其他所有流，再关闭自己
        fd.closeAll(new Closeable() {
            public void close() throws IOException {
                close0();
            }
        });
    }

   //返回该类中的 文件描述符
    public final FileDescriptor getFD() throws IOException {
        if (fd != null) {
            return fd;
        }
        throw new IOException();
    }

   //获取该类中的 文件通道类，该类就在此处使用this作为锁，进行同步
    public FileChannel getChannel() {
        synchronized (this) {
            if (channel == null) {
                //如果为空，就创建一个
               channel = FileChannelImpl.open(fd, path, true, false, this);
            }
            return channel;
        }
    }

    //本地方法
    private static native void initIDs();
    //本地方法，关闭流
    private native void close0() throws IOException;

    static {
        //设置属性的内存地址的偏移量
        initIDs();
    }

    //GC前调用的销毁方法
    protected void finalize() throws IOException {
        //这个if成立，则表示没有更多的流引用这个文件描述符，则把该流关闭，该关闭方法中，同时会关闭所有引用该文件描述符的流
        if ((fd != null) &&  (fd != FileDescriptor.in)) {
            /* if fd is shared, the references in FileDescriptor
             * will ensure that finalizer is only called when
             * safe to do so. All references using the fd have
             * become unreachable. We can call close()
             */
            close();
        }
    }
}
