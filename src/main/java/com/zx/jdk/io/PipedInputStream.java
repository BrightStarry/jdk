package com.zx.jdk.io;

import java.io.IOException;
import java.io.PipedOutputStream;

/**
 * 管道输入流
 * 将一些数据通过该类写入管道输出流
 * 一对管道的输出输入需要在多个线程间进行，单个线程会引发死锁
 *
 * 该对象维护了一个环形缓冲区，该缓冲区中有2个索引。
 * 一个是in(输入索引)，每当有数据被存入该缓冲区时，就会增加in；
 * 一个是out(输出索引)，每当从缓冲区中读取数据时，就会增加out;
 * 因为该缓冲区是环形的，所以，in最多可以一直输入数据直到out的身后(也就是即将超过out一圈)，但in无法超过out，因为一旦超过，会覆盖未读取的数据
 */
public class PipedInputStream extends InputStream {
    //写入者关闭
    boolean closedByWriter = false;
    //读取者关闭
    volatile boolean closedByReader = false;
    //连接了的
    boolean connected = false;

    /* REMIND: identification of the read and write sides needs to be
       more sophisticated.  Either using thread groups (but what about
       pipes within a thread?) or using finalization (but it may be a
       long time until the next GC). */
    //读取线程
    Thread readSide;
    //写入线程
    Thread writeSide;

    //默认通道大小
    private static final int DEFAULT_PIPE_SIZE = 1024;

    // This used to be a constant before the pipe size was allowed
    // to change. This field will continue to be maintained
    // for backward compatibility.
    //通道的 循环输入缓冲区 默认大小
    protected static final int PIPE_SIZE = DEFAULT_PIPE_SIZE;

    //环形的缓冲区(逻辑上的环形)
    protected byte buffer[];

    /**
     * 环形缓冲区中的接收数据的下个位置的索引，从通道输出流接收到的下个数据将存储在该索引位置
     * 如果<0，表示缓冲区是空的；
     * 如果in==out,表示缓冲区满(也可能表示缓冲区空了，不过这种情况都是临时的，因为一旦发生，就会把in==-1,用in==-1来表示缓冲区空了的情况)
     * 也就是每次调用 receive()方法，都会增加该索引
     * 该索引最多比out大buffer.length；因为该索引最多比out快一圈，但不能再超过out，否则会将还未读取的数据覆盖
     */
    protected int in = -1;

    //环形缓冲区中的输出数据的下个位置的索引，该索引将被该流读取
    //也就是每次调用read()方法，都会增加该索引
    protected int out = 0;


    //指定管道输出流，创建该对象
    public PipedInputStream(PipedOutputStream src) throws IOException {
        this(src, DEFAULT_PIPE_SIZE);
    }

    //指定管道输出流和缓冲区大小，创建该对象
    public PipedInputStream(PipedOutputStream src, int pipeSize)
            throws IOException {
        //初始化缓冲区
        initPipe(pipeSize);
        //连接到输出流
        connect(src);
    }

   //暂不指定输出流和缓冲区大小创建该对象，使用前必须连接好一个管道输出流
    public PipedInputStream() {
        //使用默认的缓冲区大小
        initPipe(DEFAULT_PIPE_SIZE);
    }

    //暂不指定输出流创建对象，使用前必须连接
    public PipedInputStream(int pipeSize) {
        //初始化缓冲区大小
        initPipe(pipeSize);
    }

    //初始化管道，创建对应大小的环形缓冲区
    private void initPipe(int pipeSize) {
        if (pipeSize <= 0) {
            throw new IllegalArgumentException("Pipe Size <= 0");
        }
        buffer = new byte[pipeSize];
    }

    //连接到指定管道输出流，如果该输入流已经连接了另一个管道，则抛出异常
    public void connect(PipedOutputStream src) throws IOException {
        //调用管道输出流的connect()方法连接到这个输入流
        //下面这句源码不是注释掉的，因为不是原来的包，所以this报错，暂且注解掉
        //src.connect(this);
    }


    /**
     * 接收一个字节的数据；相当于将b这个int转为byte类型，存入缓冲区
     * 如果没有输入是可用的，这个方法将阻塞
     */
    protected synchronized void receive(int b) throws IOException {
        //检查接收状态，如果不通过，该方法中会直接抛出异常
        checkStateForReceive();
        //将调用该方法的线程设置为写入线程
        writeSide = Thread.currentThread();
        //如果缓冲区满了，则使用awaitSpace()方法，等待(阻塞自己)
        if (in == out)
            awaitSpace();
        //in<0,表示之前没有开始接收数据,将in和out都设置为0，相当于开始接收输入和输出
        if (in < 0) {
            in = 0;
            out = 0;
        }
        //将b转为byte类型，存入缓冲区，并将in + 1
        buffer[in++] = (byte)(b & 0xFF);
        //最后判断下，这个表示已经将环形缓冲区一圈读完了，就从头开始再读(这也就形成了逻辑上的环形缓冲区)
        if (in >= buffer.length) {
            in = 0;
        }
    }


    /**
     * 接收数据到缓冲区，该方法会一直阻塞，直到有输入可用
     * 将b[]数组从off下标开始拷贝len个长度的数据到缓冲区
     */
    synchronized void receive(byte b[], int off, int len)  throws IOException {
        //检查输入状态，如果无法输入会抛出异常
        checkStateForReceive();
        //将当前线程设置为写线程
        writeSide = Thread.currentThread();
        //赋值
        int bytesToTransfer = len;
        //只要长度还大于0，一直循环
        while (bytesToTransfer > 0) {
            //如果缓冲区满了就调用等待方法
            if (in == out)
                awaitSpace();
            //下一个转移总数：本次循环要从b[]数组拷贝到缓冲区的数据大小
            int nextTransferAmount = 0;
            //out<in,表示 下个输出的索引 小于 下个输入的索引，也就是说 还有数据可以输出
            if (out < in) {
                //下个转让总数 = 缓冲区还可以接收的数据数，也就是直接拷贝满
                nextTransferAmount = buffer.length - in;
            //如果in<out,可能是in=-1，也就是没有数据可以输出了；
            //也可能是in已经超了out一圈，此时out可以一直输出到缓冲数组的最后，然后再从缓冲数组的0位置一直输出到in这个位置-1
            } else if (in < out) {
                //如果in==-1，表示缓冲区为空
                if (in == -1) {
                    //所以将in和out都设置为0,初始值
                    in = out = 0;
                    //然后本次循环要拷贝的数据大小就是 缓冲区的长度
                    nextTransferAmount = buffer.length - in;
                } else {
                    //此时的情况就是in超过了out一圈，但是in最多也就只能超过out一圈，不然缓冲区的数据就会被覆盖，
                    // 所以能填充的数据也就是 out -in ，也就是说，此时in只能和out平齐,然后out可以把整个缓冲区的数据都输出了才能追上in
                    nextTransferAmount = out - in;
                }
            }
            //如果 本次拷贝大小 大于 要拷贝的长度，就让它们等于， 确保 拷贝到缓冲区的数据大小 不超过 要拷贝的大小
            if (nextTransferAmount > bytesToTransfer)
                nextTransferAmount = bytesToTransfer;
            //断言， 确保 拷贝长度大于 0，否则抛出异常，这也说明上面的out不会>in,最多是等于，如果大于了，只能说明系统bug了
            assert(nextTransferAmount > 0);
            //拷贝b[]数组的数据到缓冲区，从b[]数组的off位置开始，拷贝nextTransferAmount长度的数据；到buffer[]数组的in位置开始
            System.arraycopy(b, off, buffer, in, nextTransferAmount);
            //总共要拷贝的长度 - 本次拷贝了的长度 ，递减
            bytesToTransfer -= nextTransferAmount;
            //然后偏移索引也要相应的增加，不然会出现重复拷贝的情况
            off += nextTransferAmount;
            //缓冲区的下个输入位置索引也增加
            in += nextTransferAmount;
            //如果 缓冲区满了 ，就从0开始继续输入
            if (in >= buffer.length) {
                in = 0;
            }
        }
    }

    /**
     * 检查该流的接收状态，判断此时能否接收新的数据
     */
    private void checkStateForReceive() throws IOException {
        //如果没有连接通道输出流，抛出异常
        if (!connected) {
            throw new IOException("Pipe not connected");
        //如果写入关闭或读取关闭，抛出异常
        } else if (closedByWriter || closedByReader) {
            throw new IOException("Pipe closed");
        //如果  读取线程为空 或 读取线程未激活，抛出异常
        } else if (readSide != null && !readSide.isAlive()) {
            throw new IOException("Read end dead");
        }
    }

    /**
     * 等待间隔
     * 该方法
     */
    private void awaitSpace() throws IOException {
        //只要还满着，就一直循环
        while (in == out) {
            //确保此时是可输入状态
            checkStateForReceive();
            //唤醒所有线程
            notifyAll();
            try {
                //然后自己等待1s
                wait(1000);
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException();
            }
        }
    }

    /**
     * 将 写入关闭，并唤醒所有线程
     */
    synchronized void receivedLast() {
        closedByWriter = true;
        notifyAll();
    }


    /**
     * 从通道输入流中读取下一个字节的数据；返回的是0-255之间的int类型的值;
     * 这个方法会阻塞，直到有可用的数据，或者抛出异常；
     */
    public synchronized int read()  throws IOException {
        /**
         * 流不能是关闭的，读状态不能关闭；
         * 并且（写线程不为空，写线程没激活，写线程未关闭，缓冲区为空）不能同时发生
         * 如果同时发生则表示写线程已经死亡了
         */
        if (!connected) {
            throw new IOException("Pipe not connected");
        } else if (closedByReader) {
            throw new IOException("Pipe closed");
        } else if (writeSide != null && !writeSide.isAlive()
                && !closedByWriter && (in < 0)) {
            throw new IOException("Write end dead");
        }

        //将当前线程设置为读线程
        readSide = Thread.currentThread();
        /**
         * 如果循环中发生下面的写线程存在，但未激活异常，最多重试2次，代码如下(该代码在循环中)：
         *  if ((writeSide != null) && (!writeSide.isAlive()) && (--trials < 0)) {
         *   throw new IOException("Pipe broken");
         *   }
         *   因为用的&&,一旦第一或第二个条件不成立，则不会继续，所以除非都成立，也就是说异常发生了，才会进行--操作，
         *   那么就可以理解为，一旦发生了2次这种异常，就抛出异常
         */
        int trials = 2;
        /**
         * 如果下个输入位置索引 < 0，也就是说，没有数据可读 就一直循环、阻塞，不停的唤醒其他(写)线程，等待数据输入
         */
        while (in < 0) {
            //如果写被关闭了，返回 -1，表示到末尾了
            if (closedByWriter) {
                return -1;
            }
            //如果写线程存在 且 写线程未激活 且 trials - 1<0，则抛出异常，表明通道残缺
            if ((writeSide != null) && (!writeSide.isAlive()) && (--trials < 0)) {
                throw new IOException("Pipe broken");
            }
            //唤醒所有线程，因为in<0,可能是等待写入，所以唤醒所有线程，让写线程进行写操作
            notifyAll();
            try {
                //然后自己等待1s
                wait(1000);
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException();
            }
        }
        //此时表示有可读的数据，读取一个字节，并将out(下个输出位置索引+1)
        int ret = buffer[out++] & 0xFF;
        //输出完一圈后，重置到0位置
        if (out >= buffer.length) {
            out = 0;
        }
        //如果此时out==in了，也就是out+1后等于in了，也就说明缓冲区已经没有数据可读了，就将in设为-1
        if (in == out) {
            in = -1;
        }
        //返回读取到的字节，int型
        return ret;
    }

    /**
     * 读取该流中的len长度的数据到b[]数组的off位置
     */
    public synchronized int read(byte b[], int off, int len)  throws IOException {
        /**
         * 确保参数符合规范
         */
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        /* possibly wait on the first character */
        //可能是等待的第一个字符(翻译，没看明白)
        //调用上面的read()方法，从该流中读取一个字符
        int c = read();
        //如果没有读取到，就返回-1
        if (c < 0) {
            return -1;
        }
        //将读取到的字节填入数组
        b[off] = (byte) c;
        //设置该方法当前读取了的字节长度，当前为1
        int rlen = 1;
        //如果缓冲区还有数据，并且要读取的长度大于1（如果=1的话，上面读取了一个字节，该方法已经可以直接返回了），就一直循环
        while ((in >= 0) && (len > 1)) {
            //定义本地循环要读取的字节长度
            int available;
            //in>out，表示有可读数据，但是in没有超过out一圈
            if (in > out) {
                //那么此处应该就是读取的in - out的数据量，我不清楚为什么in-out会有可能比buffer.length - out大
                available = Math.min((buffer.length - out), (in - out));
            } else {
                //这个则 in <= out 也就是说in已经超过了out一圈，那就先把out 到 缓冲区终点(终点和起点视同一点)读取完,
                //那也就是读取到缓冲区末尾，此时，缓冲区的0 - （in-1）位置还是可读的
                //此外，之所以要分两次读取的原因显而易见，环形缓冲区的本质还是一个byte[]，每次拷贝最多只能拷到数组末尾
                available = buffer.length - out;
            }

            //因为循环循环前，已经读取了一个字节，所以此处-1
            //此处是确保 本次循环要读取的字节长度 不超过 剩余要读取的字节长度
            if (available > (len - 1)) {
                available = len - 1;
            }
            //将该流缓冲区中的数据读取到b[]数组
            System.arraycopy(buffer, out, b, off + rlen, available);
            //递增 输出位置的索引
            out += available;
            //递增 该方法当前读取了的字节长度
            rlen += available;
            //将要读取的长度递减
            len -= available;
            //如果out索引 到达末尾了，重置
            if (out >= buffer.length) {
                out = 0;
            }
            //此时的out==in，是out的结果，也就是说不是缓冲区满了，而是缓冲区没了，所以将in置为-1，表示缓冲区空了
            if (in == out) {
                in = -1;
            }
        }
        //返回读取到的字节长度
        return rlen;
    }

    /**
     * 返回 剩余可读的字节数
     */
    public synchronized int available() throws IOException {
        //如果 输入索引 小于0，即-1，则表示没有可读取字节，所有返回0
        if(in < 0)
            return 0;
        //如果in==out，表示缓冲区的所有字节都是可读取的，所以直接返回缓冲区大小
        else if(in == out)
            return buffer.length;
        //因为in是下个要输入的索引，out是下个要读取的索引；那么在环形缓冲区中，in肯定是大于out的
        // 那么下个要输入的索引 - 下个要读取的索引，自然就是剩余可读取字节数
        else if (in > out)
            return in - out;
        else
            //这个else表示 in < out,这种情况表示 输入索引已经比 输出索引快了一圈了，也就是说，
            //此时out可以一直输出到缓冲数组的最后，然后再从缓冲数组的0位置一直输出到in这个位置-1
            //所以此时的可读数据量就是下面这样
            return in + buffer.length - out;
    }

    //关闭
    public void close()  throws IOException {
        //将 关闭的读取者 设为true
        closedByReader = true;
        //同步的
        synchronized (this) {
            //将缓冲区中输入的索引设为-1，表示没有缓冲
            in = -1;
        }
    }
}
