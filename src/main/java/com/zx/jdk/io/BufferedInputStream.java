package com.zx.jdk.io;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
/**
 * 缓冲输入流类-处理流-装饰者
 * 该类中的  pos、count、markpos、mark()、reset()全都是针对缓存数组的，而非真正的流
 */
public class BufferedInputStream extends FilterInputStream {
    //默认最大缓存大小
    private static int DEFAULT_BUFFER_SIZE = 8192;
    //JVM规定的缓存上限
    private static int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;
    //缓存数组
    protected volatile byte buf[];
    //原子引用字段修改器，在创建时，指定了类、字段类型、字段名，可以使用这个类，对指定类的该字段buf，进行原子的替换等操作
    private static final
    AtomicReferenceFieldUpdater<BufferedInputStream, byte[]> bufUpdater =
            AtomicReferenceFieldUpdater.newUpdater
                    (BufferedInputStream.class,  byte[].class, "buf");
    //索引，大于buf[]数组中的最后一个有效字符（可读取的字符数）的位置;非负数，小于buf[]长度;
    // buf[0]-buf[count-1]是可以从流中读取的字节
    //count可以理解为，最大允许读取的字节数
    protected int count;
    //从buf[]中读取的下一个字节的索引，不能为负数，不能大于count;buf[pos]是要读取的下一个字节,
    protected int pos;
    /**
     * 最后一次调用mark()方法时pos的值；默认为-1；
     * 如果它不为-1，则从buf[markpos]-buf[pos-1]位置的所有字节，必须保存在缓冲数组中(不过，可以移动到缓存数组的另一个位置，适当的调整count、pos、markpos的值),
     * 除非pos与markpos之间的差超过了marklimit，也就是标记后，一直往后读取，正在读取的位置与被标记位置间的字节超过限制
     */
    protected int markpos = -1;
    //mark后最大可读字节数 ，调用mark()方法后，pos - markpos 不能超过该值，否则reset()会失败，且markpos重置为-1
    protected int marklimit;
    //如果属性 in输入流不为空，则返回in（该属性在FilterInputStream中，也就是装饰者模式的被装饰者）
    private InputStream getInIfOpen() throws IOException {
        InputStream input = in;
        if (input == null)
            throw new IOException("Stream closed");
        return input;
    }
    //如果缓存数组不为空，则返回该缓存数组，否则抛出异常
    private byte[] getBufIfOpen() throws IOException {
        byte[] buffer = buf;
        if (buffer == null)
            throw new IOException("Stream closed");
        return buffer;
    }
    //创建该类，需要传入 被装饰者 - 一个InputStream的节点流, 缓存大小为默认值
    public BufferedInputStream(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }
    //创建该类，同样传入被装饰者，同时指定缓存大小
    public BufferedInputStream(InputStream in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
    }

    /**
     * 用更多的数据填充缓存数组，处理mark标记；
     * 假定该方法被某个同步方法调用，则默认缓存数组的所有数据已经被读取完毕，所以pos>count（翻译原文档的）
     *
     * 反正这个方法就是用扩展缓冲区的方法来解决，当mark标记后，不断读取字节，然后缓冲区满了，需要重置时，不然mark标记失效的问题
     *
     * 1. 当没有mark时，重新读取字节到缓冲区
     * 2. 当mark，且缓冲区读完了，且mark在缓冲区中的某个位置(不是0这个位置),那就放弃mark前的所有数据，并补满缓冲区
     * 3. 当mark，且缓冲区读完了，且mark后读取的字节数超出marklimit限制，那就将mark设为无效，然后重新读取字节到缓冲区
     * 4. 当mark，且缓冲区读完了，且缓冲区的长度超过了 最大缓冲区的限制，则抛出异常(这种情况通常是marklimit设置太大，且已经调用多次fill()方法对缓冲区进行扩充的问题)
     * 5. 当mark，且缓冲区读完了，且上面的都没有发生（也就是说，是从缓冲区的0索引开始mark，且未超出各类限制）；那就对缓冲区进行扩充，直接扩大2倍，当然，要小于最大缓冲限制和marklimit限制
     * 上面操作结束后，都需要执行的是：将缓冲区补满(使用被装饰者的read()方法读取字节到缓冲区)
     */
    private void fill() throws IOException {
        //获取缓存数组
        byte[] buffer = getBufIfOpen();
        //如果markpos<0,也就是mark()方法未生效，则设置 下个读取位置(pos)从0开始
        //也就是说，如果没有mark()，则重新读取缓存数组
        if (markpos < 0)
            pos = 0;
        //如果有mark(),且下个读取的索引(pos)大于缓存数组长度，也就是说，mark后，还读完了缓存的数据
        else if (pos >= buffer.length)
            /**
             * 且,如果mark()时，已经读取了部分数据(也就是mark()时，pos不为0)；也就是说，不是在还没开始读取缓存数组的时候，就调用的mark()
             * 下面这个if的操作就是  将缓存数组 重置为 从标记位置往后的所有字节 的一个数组，然后继续从之前的位置读取；放弃了已读的且mark前的数据
             */
            if (markpos > 0) {  /* can throw away early part of the buffer */
                //mark()后，已经读取的字节数
                int sz = pos - markpos;
                //该操作将buff[]数组的一些数据复制到它自己的从0开始的位置，
                //也就是抛弃了mark标记前的所有数据
                System.arraycopy(buffer, markpos, buffer, 0, sz);
                //将 下个读取索引(pos)  设置为  mark()后，已经读取的字节数
                //也就上面的操作，相当于是：放弃了缓存数组中mark标记前的数据，然后还是从操作之前的位置读取
                pos = sz;
                //将markpos设置为0，因为之前的markpos在当前缓存数组中，是0这个索引
                markpos = 0;
            /**
             * 因为之前的外层if，表明此时，已经是mark了，且把缓冲区读完了，
             * 且，上面的if(markpos>0)没有执行，则表示之前mark时，还没有读取缓冲区数据，是在pos为0时mark的。
             * 此处又判断出 缓冲区的长度 大于 mark的最大读取限制
             * 也就是说，此时 上个mark后，读取的字节数已经超出 mark的最大读取限制，则将上个mark变为无效
             */
            } else if (buffer.length >= marklimit) {
                //则缓存过大，将mark设置为无效
                markpos = -1;
                //然后从头开始读取缓冲区
                pos = 0;
            /**
             * 如果缓存数组长度 大于等于 最大缓存长度
             * 也就是说，虽然 上个mark后，没有超出marklimit，但是数组的长度超出上限了
             */
            } else if (buffer.length >= MAX_BUFFER_SIZE) {
                //抛出异常Error
                throw new OutOfMemoryError("Required array size too large");
            /**
             * 如果上面的都没有成立
             * 也就是说：该流被mark了；然后读取完了缓冲区所有的数据；然后mark也没有超出一些规定的大小；而且mark的位置不是缓冲区的0索引的位置,
             * 这样，如果什么都不做，mark就失效了，所以就需要扩展缓冲区，让mark不失效
             */
            } else {
                /**
                 * 这个else下面代码的作用就是 扩大缓冲区
                 */
                //如果 最大缓冲区大小 是 pos(此时的pos肯定是和缓冲区大小相同的)的 2倍以上，那么就取 2 * pos（也就是2倍的当前缓冲区大小）,否则取 最大缓冲区大小
                //这个nsz不会是 new size 吧，够简练的，
                int nsz = (pos <= MAX_BUFFER_SIZE - pos) ?
                        pos * 2 : MAX_BUFFER_SIZE;
                //并且 上面这个值 也不能超过 mark的限制大小
                if (nsz > marklimit)
                    nsz = marklimit;
                //创建一个该值大小的字节数组，这个应该就是 new buffer了
                byte nbuf[] = new byte[nsz];
                //将原缓冲区中的所有数据拷贝到 该数组nbuf[]中去，拷贝的长度是pos(此时应该是原缓冲区的长度，反正就是已经读取了的数据)
                System.arraycopy(buffer, 0, nbuf, 0, pos);
                /**
                 * bufUpdater.compareAndSet(this, buffer, nbuf) 该方法第一个参数为要替换字段值的对象，第二个为期望值，第三个为新值；如果对象的字段的原值等于 期望值，则将其替换为 新值
                 * 此处是，如果该流的缓冲区到现在还是原先的值，就把缓冲区原子地替换为上面这个nbuf[]，也就是扩展过的缓冲区
                 */
                if (!bufUpdater.compareAndSet(this, buffer, nbuf)) {
                    // Can't replace buf if there was an async close.
                    // Note: This would need to be changed if fill()
                    // is ever made accessible to multiple threads.
                    // But for now, the only way CAS can fail is via close.
                    // assert buf == null;
                    /**
                     * 上面这段话的意思，应该是，曾经可能会发生多个线程存取的情况，导致其CAS失败(因为没有同步？)；
                     * 但现在，除非是流被关闭，否则是不会失败的，所以此刻的失败是因为 buf==null,
                     * 所以抛出异常，流被关闭；
                     */
                    throw new IOException("Stream closed");
                }
                //将方法中的缓冲区也替换为新的缓冲区
                buffer = nbuf;
            }
        //因为缓冲区大小变化了，所以count(最大允许读取字节数)也要变化，先让它等于pos，下面加上读取到的字节数后，也就是最大允许读取的字节数了
        count = pos;
        //使用被装饰者的方法将缓冲区读取满
        int n = getInIfOpen().read(buffer, pos, buffer.length - pos);
        //如果读取到了字节，则将pos+读取到的字节数，也就是， 最大能读取的字节数(count)
        if (n > 0)
            count = n + pos;
    }

    /**
     * 读取下一个字节
     * 不过对于这个缓冲流来说，该方法会先判断缓冲区中是否还有数据，如果没了就一次性把缓冲区补满，然后读取的时候，读取的就是缓冲区的数据
     */
    public synchronized int read() throws IOException {
        //如果 当前下个要读取的字节索引 超过或等于 最大允许读取字节数；也就是缓冲区满了
        if (pos >= count) {
            //调用该方法解决缓冲区过小问题，如果没有什么问题，它就只是进行普通的重新读取缓冲区的操作
            fill();
            //如果调用后还是超过或等于，则表示没有可读字节了
            if (pos >= count)
                return -1;
        }
        //如果缓冲区还有可读数据，则返回缓冲区中下一个字节的数据;
        return getBufIfOpen()[pos++] & 0xff;
    }

    /**
     * 将数据读取到数组中，一般只读取一部分，如果有必要，也可以直接将缓冲区中的所有数据读取到该数组中
     */
    private int read1(byte[] b, int off, int len) throws IOException {
        //剩余可读字节数
        int avail = count - pos;
        //如果缓冲区中没有可读的字节了，则从流中补充
        if (avail <= 0) {
            //如果读取的长度大于等于缓冲区大小，且没有使用mark，则不通过缓冲区，直接读取
            if (len >= getBufIfOpen().length && markpos < 0) {
                return getInIfOpen().read(b, off, len);
            }
            //否则就补充缓冲区
            fill();
            //然后再次判断可读字节数
            avail = count - pos;
            //如果还是没有表示流中也读完了，则返回-1
            if (avail <= 0) return -1;
        }
        //取 剩余可读字节数 和 要读取字节数 的较小值
        int cnt = (avail < len) ? avail : len;
        //拷贝/读取字节到b[]数组
        System.arraycopy(getBufIfOpen(), pos, b, off, cnt);
        //给 下个读取的字节索引 加上前面读取的字节数
        pos += cnt;
        //返回读取的字节数
        return cnt;
    }

    /**
     * 读取指定长度的数据到b[]数组的off位置后
     */
    public synchronized int read(byte b[], int off, int len)
            throws IOException
    {
        //只是为了检查该流是否关闭
        getBufIfOpen();
        //具体不祥，反正就是一种运算符，就是为了判断这些必须大于等于0，且偏移量+要读取的长度不能大于数组长度，否则抛出下标越界异常
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int n = 0;
        for (;;) {
            //使用read1()方法进行读取，返回读取到的字节数
            int nread = read1(b, off + n, len - n);
            //如果没有可读的字节了
            if (nread <= 0)
                //如果是第一次循环，就直接返回读取到的字节数，否则返回n(这个n应该是个累加器，累加每次循环读取到的字节数，最后返回就是总的读取到的字节数)
                return (n == 0) ? nread : n;
            //累加
            n += nread;
            //如果读取到的字节数大于(应该不会大于)等于要读取的字节数，则直接返回
            if (n >= len)
                return n;
            //调用被装饰者，如果流没有关闭，且可读字节数没了，也直接返回
            InputStream input = in;
            if (input != null && input.available() <= 0)
                return n;
        }
    }

    //跳过n长度的字节
    public synchronized long skip(long n) throws IOException {
        //使用该方法只是为了检查这个流是不是关闭的，如果关闭，则该方法会抛出异常
        getBufIfOpen(); // Check for closed stream
        //要跳过的字节数不能小于0
        if (n <= 0) {
            return 0;
        }
        //缓存数组中剩余可读的字节数
        long avail = count - pos;
        //如果没有可读字节了
        if (avail <= 0) {
            // If no mark position set then don't keep in buffer
            //而且，如果mark()未被使用，则直接使用 被装饰者的skip()方法即可
            if (markpos <0)
                return getInIfOpen().skip(n);

            // Fill in buffer to save bytes for reset
            //填充缓存数组，
            fill();
            //当前缓存数组中 剩余可读字节数
            avail = count - pos;
            //如果可用的字节数还是没有，则返回跳过0个字节
            if (avail <= 0)
                return 0;
        }
        //判断 当前剩余可读字节数 和 要跳过字节数的大小，跳过小的那个
        long skipped = (avail < n) ? avail : n;
        //更改索引，也就是跳过对应字节数
        pos += skipped;
        //返回跳过的字节数
        return skipped;
    }

    /**
     * 返回剩余可读取的字节数的估计值，不会被下一个调用此流的方法阻塞
     */
    public synchronized int available() throws IOException {
        //最大允许读取字节数 - 当前下个要读取的字节索引 ， 则为 剩余可读字节数
        //这个算出来的是缓存数组中的 剩余可读字节数
        int n = count - pos;
        //再使用 被包装者，也就是该类中组合的处理流的该方法获取 剩余可读字节数
        //这个数才是真正的流中的剩余可读字节数
        int avail = getInIfOpen().available();
        //如果 缓存中的剩余可读 +　流中的剩余可读　> Integer.Value，则直接返回Integer.value,否则返回两者之和
        return n > (Integer.MAX_VALUE - avail)
                ? Integer.MAX_VALUE
                : n + avail;
    }

    //标记当前位置，并设置 最大允许往后读取的范围， 如果超出则本次mark失效
    public synchronized void mark(int readlimit) {
        //将 最大允许往后读取的范围 = 传入的值
        marklimit = readlimit;
        //将mark索引 = 当前下个要读取的索引
        markpos = pos;
    }

    //回退到mark()位置
    public synchronized void reset() throws IOException {
        //获取缓存数组，如果流已关闭，会引发异常
        getBufIfOpen();
        //如果markpos<0,则表示mark()方法未使用，抛出异常
        if (markpos < 0)
            throw new IOException("Resetting to invalid mark");
        //执行到这步，表示进行重置操作，将 下个要读取字节索引 重置 为 标记索引
        pos = markpos;
    }

    /**
     * 返回该流是否支持 mark()、reset()方法，因为支持，所以直接返回true
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * 关闭该流，并释放相关的流的系统资源
     * 如果一个流是关闭的，那么对该流的许多方法的调用将抛出异常
     * 关闭关闭过的流，是没有问题的
     *
     * 之所以不同步，是因为如果此时流在read()，那么会一直无法关闭该流
     * 而关闭方法就是为了能随时关闭该流
     * 所以一直循环，尝试在read()的间隙，使用原子操作将缓冲区设置为null，然后将流设置为null，以关闭该流
     */
    public void close() throws IOException {
        //定义一个为null的字节数组
        byte[] buffer;
        //如果buffer和buf不为null，也就是没有关闭成功，就一直关闭
        //反正就是执行close方法后，就一直在这循环，等到读取完了缓冲区(缓冲区为null了)，就把流关闭
        while ( (buffer = buf) != null) {
            //如果该类的缓冲区为空，就使用原子修改器将该对象的缓冲区替换为null
            if (bufUpdater.compareAndSet(this, buffer, null)) {
                /**
                 * 这个关闭就是直接将所有对该 被装饰者 的引用取消掉，然后等GC回收它
                 * 至于下面还判断一次!= null，可能是为了防止指令重排序?不确定
                 */
                InputStream input = in;
                in = null;
                if (input != null)
                    input.close();
                return;
            }
            // Else retry in case a new buf was CASed in fill()
        }
    }
}
