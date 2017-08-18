package com.zx.jdk.io;
import java.io.InputStream;
import java.io.IOException;
/**
 * 回推输入流，包装一个其他流，然后就多一个功能，能往缓冲区里添加字节数据，那么下次调用read()方法，读取的就是回推回去的数据
 * 在某些情况下有用。
 * 例如这些字节数据中有一个标识符，规定每次读取只能读取标识符那么多的数据，但是不确定标识符之间的数据长度；
 * 就可以从该流中先读取出若干字节，如果读到了标识符，就将标识符后的字节回推回去；如果没有，就继续读取，直到读取到标识符；
 * 所以该流可以拿来处理TCP连接的粘包问题
 */
public class PushbackInputStream extends FilterInputStream {
    //缓冲区
    protected byte[] buf;

    //缓冲区中 下个要读取的字节的位置，当pos==buf.length时，表示缓冲区为空；当pos==0时，表示缓冲区满了
    protected int pos;

    //确保流不是关闭的，如果关闭，抛出异常
    private void ensureOpen() throws IOException {
        if (in == null)
            throw new IOException("Stream closed");
    }

    /**
     * 创建该流，传入 被包装流 和 缓冲区大小
     */
    public PushbackInputStream(InputStream in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("size <= 0");
        }
        this.buf = new byte[size];
        //默认缓冲区为空的
        this.pos = size;
    }

    //创建该流，传入被包装流，缓冲区大小使用默认的1
    public PushbackInputStream(InputStream in) {
        this(in, 1);
    }

    /**
     * 读取下一个字节
     */
    public int read() throws IOException {
        //检查 被包装流不为空/未关闭
        ensureOpen();
        //并且缓冲区中还有数据
        if (pos < buf.length) {
            //返回缓冲区中的下一字节
            return buf[pos++] & 0xff;
        }
        //如果缓冲区没数据了，直接调用被包装流 从流中读取
        return super.read();
    }

    /**
     * 读取len长度的字节到b[]数组的off位置往后
     */
    public int read(byte[] b, int off, int len) throws IOException {
        //确保流未关闭
        ensureOpen();
        /**
         * 确保参数无误
         */
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        //该方法真正要读取的字节数 ，先让它等于 缓冲区剩余可读字节数
        int avail = buf.length - pos;
        //如果有 剩余可读的
        if (avail > 0) {
            //如果 要读取的长度小于 剩余可读的
            if (len < avail) {
                //那就可以直接读取 要读取的长度
                avail = len;
            }
            //拷贝缓冲区从pos开始的avail(真正要读取的字节数)长度的数据 到 b[]数组的off位置开始
            System.arraycopy(buf, pos, b, off, avail);
            //下个要读取的索引 要增加对应的值
            pos += avail;
            //偏移量 要增加对应的值； 因为下面可能还要直接从流中读取剩余的长度的字节到b[]数组
            off += avail;
            //将要读取的长度 - 真正读取了的长度 ，也就是剩下的还要读取的长度
            len -= avail;
        }
        //如果还有要读取的
        if (len > 0) {
            //使用 被包装流的 方法读取相应长度的字节
            //返回的len是从流中真正读取到的长度
            len = super.read(b, off, len);
            //如果流中没数据了
            if (len == -1) {
                //那么如果之前从缓冲区读到数哭了，就返回 对应的长度；
                //如果没有，就返回-1，表示缓冲区和流中都没有可读字节
                return avail == 0 ? -1 : avail;
            }
            //执行这句，表示从流中读取到了数据，就将 从缓冲区读到的数据长度 加上 从中读到的数据长度 ，也就是真正读到的字节长度
            return avail + len;
        }
        //执行这句，表示已经从缓冲区读到了对应长度的字节数， 直接返回 真正读取到的字节数(如果不出意外，肯定是等于len的)
        return avail;
    }

    /**
     * 未读取方法
     * 回推 一个字节的数据到 缓冲区
     */
    public void unread(int b) throws IOException {
        //确保流打开
        ensureOpen();
        //如果pos==0，表示缓冲区是满的
        if (pos == 0) {
            throw new IOException("Push back buffer is full");
        }
        //此处先将pos-1，并且将字节存入缓冲区的pos-1后的位置，也就完成了回推
        //下次读取将从pos读取，读取到的也就是这个回推的字节了
        buf[--pos] = (byte)b;
    }


    /**
     * 未读取方法
     * 将传入的b[]数组中从off位置开始的len个字节 回推回缓冲区(buf[])
     * 那也就是说，接下来使用read()方法从缓冲区读取的下一个字节就是原来b[off]，再下一个就是b[off+1]，如此类推
     *
     */
    public void unread(byte[] b, int off, int len) throws IOException {
        //确保流打开
        ensureOpen();
        //如果 要回推的字节长度 大于 缓冲区下个要读取的字节位置的索引；那么是无法回推的
        //因为所谓的回推就是将数据复制会缓冲区，并且是不能覆盖当前还未读取的数据的(也就是buf[pos]到buf[buf.length -1])
        //所以，最多可回推的字节数就是pos的长度
        //所以调用该方法时要小心，len不能过长
        if (len > pos) {
            throw new IOException("Push back buffer is full");
        }
        //将pos 减去 对应的值，然后将 要回推的数据拷贝到缓冲区，表示回推完成
        pos -= len;
        System.arraycopy(b, off, buf, pos, len);
    }

    /**
     * 未读取方法
     * 只传入一个数组，默认off为0，len为数组长度
     */
    public void unread(byte[] b) throws IOException {
        unread(b, 0, b.length);
    }

   //返回剩余可用字节数
    public int available() throws IOException {
        //确保流是打开的
        ensureOpen();
        //缓冲区剩余可读字节大小
        int n = buf.length - pos;
        //被包装流的 剩余可读大小
        int avail = super.available();
        //返回 缓冲区可读字节 + 流中可读字节数 = 剩余可读字节数，当然，不允许其超过Integer。MAX_VALUE
        return n > (Integer.MAX_VALUE - avail)
                ? Integer.MAX_VALUE
                : n + avail;
    }

   //跳过n个字节
    public long skip(long n) throws IOException {
        //检查流未关闭,关闭则抛出异常
        ensureOpen();
        //确保要跳过的字节数不小于0
        if (n <= 0) {
            return 0;
        }
        //真正要跳过的字节数，默认为 缓冲区剩余可读字节数
        long pskip = buf.length - pos;
        //如果>0
        if (pskip > 0) {
            //如果 n 未超过缓冲区剩余可读字节数,那就跳过 n
            if (n < pskip) {
                pskip = n;
            }
            //增加索引，即表示跳过了
            pos += pskip;
            // 剩余要跳过的字节数
            n -= pskip;
        }
        //如果 还有字节数没跳， 调用 被包装流 自己的方法，跳过
        if (n > 0) {
            pskip += super.skip(n);
        }
        //返回跳过的字节数
        return pskip;
    }

    //是否支持mark，不支持
    public boolean markSupported() {
        return false;
    }

    //标记方法，该流不支持
    public synchronized void mark(int readlimit) {
    }

    //回滚方法，该流不支持
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    //关闭流，调用 被包装流自己的close()方法，然后将其设为null
    public synchronized void close() throws IOException {
        if (in == null)
            return;
        in.close();
        in = null;
        buf = null;
    }
}
