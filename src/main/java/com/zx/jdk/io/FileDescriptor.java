package com.zx.jdk.io;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.util.ArrayList;
import java.util.List;
/**
 * 文件描述类
 * 用来表示开放的 一个文件、一个socket或其它源等；
 * 它可以当作一个文件，但无法对其进行操作
 * 主要作用就是创建一个FileInputStream或FileOutputStream来组合(将它作为成员变量)它；不应该自己创建该类
 *
 */
public final class FileDescriptor {
    //文件描述值
    private int fd;
    //文件描述句柄（句柄：特殊的智能指针，可以是一个窗口、按钮、图标、文件等；是一个唯一整数，可以理解为ID）
    private long handle;

    //存储一个实现Closeable接口的类(流)
    private Closeable parent;
    //存储其他一堆实现Closeable接口的类，如果parent不为空，然后又加入了一个实现Closeable的类，那parent也会被添加到otherParents中
    private List<Closeable> otherParents;
    //是否关闭
    private boolean closed;

    /**
     * 创建一个 无效的 该类对象
     */
    public /**/ FileDescriptor() {
        fd = -1;
        handle = -1;
    }

    //静态代码块
    static {
        //执行初始方法
        //该方法是一个JNI(Java Native Interface，java调用了其他语言进行操作)方法
        // 其作用是设置该类(FileDescriptor)的属性的内存地址偏移量,便于在必要时操作内存给他赋值
        //设置了fd和handle属性的内存地址偏移量
        initIDs();
    }

    // Set up JavaIOFileDescriptorAccess in SharedSecrets
    static {
        sun.misc.SharedSecrets.setJavaIOFileDescriptorAccess(
                new sun.misc.JavaIOFileDescriptorAccess() {
                    public void set(FileDescriptor obj, int fd) {
                        obj.fd = fd;
                    }

                    public int get(FileDescriptor obj) {
                        return obj.fd;
                    }

                    public void setHandle(FileDescriptor obj, long handle) {
                        obj.handle = handle;
                    }

                    public long getHandle(FileDescriptor obj) {
                        return obj.handle;
                    }
                }
        );
    }

    /**
     * 一个标准文件输入流句柄。该对象不是直接使用的，而是通过System.in
     */
    public static final FileDescriptor in = standardStream(0);

    /**
     * 一个标准文件输出流句柄。该对象不是直接使用的，而是通过System.out
     */
    public static final FileDescriptor out = standardStream(1);

    /**
     * 一个标准文件输出流句柄。该对象不是直接使用的，而是通过System.error
     */
    public static final FileDescriptor err = standardStream(2);

    /**
     * 验证该文件描述符是否有效，也就是 handle 和 fd 都不为-1
     */
    public boolean valid() {
        return ((handle != -1) || (fd != -1));
    }

    /**
     * 强制所有系统缓冲区与底层设备同步。 该方法在将此FileDescriptor的所有修改数据和属性都写入相关设备后返回。
     * 特别地，如果该FileDescriptor引用诸如文件系统中的文件的物理存储介质，同步将不会返回，
     * 直到与该FileDescriptor相关联的缓冲区的所有内存内修改的副本已被写入物理介质。
     * 同步意图由需要物理存储（如文件）的代码用于已知状态例如，提供简单事务处理的类可能会使用sync来确保由给定的文件引起的对文件的所有更改交易记录在存储介质上。
     * sync只影响此FileDescriptor下游的缓冲区。 如果应用程序正在执行任何内存缓冲（例如，通过BufferedOutputStream对象），
     * 那么这些缓冲区必须在数据受同步影响之前刷新到FileDescriptor中（例如调用OutputStream.flush）。
     */
    public native void sync() throws SyncFailedException;

    /* This routine initializes JNI field offsets for the class */
    //JNI方法，初始化方法
    private static native void initIDs();
    //JNI方法，应该是设置fd的值，然后返回对应的句柄
    private static native long set(int d);

    //使用fd(文件描述值)构建一个标准文件描述符
    private static FileDescriptor standardStream(int fd) {
        //创建该类对象，此时该对象的fd 和 handle都是-1
        FileDescriptor desc = new FileDescriptor();
        //设置fd的值，并返回对应的句柄，并赋值给该对象的句柄属性
        desc.handle = set(fd);
        return desc;
    }

    /*
     * Package private methods to track referents.
     * If multiple streams point to the same FileDescriptor, we cycle
     * through the list of all referents and call close()
     */
    /**
     * 如果多个流引用同一个文件描述符，我们会通过循环列表中的所有引用来调用关闭方法
     */

    /**
     * 追加一个实现可关闭接口的类(流对象)到该对象，
     * 每追加一次，也就是有一个流引用了该文件描述符
     */
    synchronized void attach(Closeable c) {
        //如果parent还为空，将将它赋值给parent
        if (parent == null) {
            parent = c;
        //如果otherParents集合还为空，创建出来，并将parent和它都放进去
        } else if (otherParents == null) {
            otherParents = new ArrayList<>();
            otherParents.add(parent);
            otherParents.add(c);
        //如果parent和otherParents都不为空，就直接将它增加到otherParents中
        } else {
            otherParents.add(c);
        }
    }

    /**
     * 通过循环来调用close()方法关闭每个Closeable
     *
     * 先是关闭其他所有引用该类的流，再通过rerleaser关闭调用该方法的流
     */
    synchronized void closeAll(Closeable releaser) throws IOException {
        //如果是没关闭的（没调用过该方法）
        if (!closed) {
            //将closed置为true，表示调用过该方法了
            closed = true;
            //定义一个IO异常
            IOException ioe = null;
            //这个是JDK7中的写法，可以在try-catch结束后自动调用close()方法关闭该类，不过该类必须实现Closeable接口
            try (Closeable c = releaser) {
                //如果集合不为空，
                if (otherParents != null) {
                    //遍历集合
                    for (Closeable referent : otherParents) {
                        //调用每个元素的close()方法
                        try {
                            referent.close();
                        } catch(IOException x) {
                            //如果抛出异常，都将异常压制到ioe中
                            //可以通过getSuppressed()方法获取这些异常
                            //将这些异常都压制到一起是为了防止这些异常丢失，方便提取所有的异常
                            if (ioe == null) {
                                ioe = x;
                            } else {
                                ioe.addSuppressed(x);
                            }
                        }
                    }
                }
            } catch(IOException ex) {
                /*
                 * If releaser close() throws IOException
                 * add other exceptions as suppressed.
                 */
                /**
                 * 如果跑出了异常，就将ioe增加到这个异常中
                 */
                if (ioe != null)
                    ex.addSuppressed(ioe);
                //这句只是为了在finally中抛出异常，因为ex的作用域太小
                ioe = ex;
            } finally {
                //如果发生了异常，抛出
                if (ioe != null)
                    throw ioe;
            }
        }
    }
}
