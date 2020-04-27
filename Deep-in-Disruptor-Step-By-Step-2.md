Deep-in-Disruptor-Step-By-Step-2

# 1. 源码分析

## 1.1 类图

![image-20200427214053733](http://q8xc9za4f.bkt.clouddn.com/cloudflare/image-20200427214053733.png)



## 1.2 时序图



![image-20200427214153169](http://q8xc9za4f.bkt.clouddn.com/cloudflare/image-20200427214153169.png)





# 2. 底层性能突出的原因

1. 数据结构： 使用环形结构，数组，内存预加载
2. 单线程写方式，内存屏障
3. 消除伪共享（填充缓存行）
4. 序号栅栏和序号配合使用来消除锁和CAS;





# 3. 源码分析；

参考

https://github.com/peizihui/disruptor-ChineseVersion-master.git



# 4. 高性能之道

##4.1 内核 使用单线程写

- RingBuffer 可以做到无锁，“单线程写”，前提的前提；

- Redis ,Netty 都是这个设计思想；

  **个人总结**

  //  高性能，单线程;

  //  高并发 ，多线程; 

  注意是有区别的；

  

  

  

##4.2 系统内存优化-内存屏障

  1. 要正确实现无锁，需要另外一个关键技术，内存屏障；
  2. 对应JAVA语言，就是valotile变量与happens before 语义；
  3. 内存屏障-Linux的smp_wmb()(内存的写)/smp_rmb()（内存读）; linux kfifo ;

  

  ## 4.3 系统缓存优化-消除伪共享

1. 缓存系统中是以缓存行（cache line）为单位存储的；
2. 缓存行是2的整数幂个连续字节，一般为32-256字节
3. 常见的缓存行大小是64个字节；
4. 多线程修改相互独立变量时，如果变量共享同一个缓存行就会无意中影响彼此的性能，这就是伪共享；

| x =10                                   |
| --------------------------------------- |
| (左边填充7个LONG) y=8 （右边填充7LONG） |
| z=15                                    |

**描述**

  若两个线程分别访问Y,Z，分别在不同的缓存行空间，不会导致共享缓存行造成的彼此影响，彼此性能更高，

用空间换时间。





```
***
 *  缓存系统，缓存行设计；
 * 1. 缓存系统中是以缓存行（cache line）为单位存储的；
 * 2. 缓存行是2的整数幂个连续字节，一般为32-256字节
 * 3. 常见的缓存行大小是64个字节；
 * 4. 多线程修改相互独立变量时，如果变量共享同一个缓存行就会无意中影响彼此的性能，这就是伪共享；
 *
 *  LhsPadding 类似占位符，
 *
 */
class LhsPadding
{
    protected long p1, p2, p3, p4, p5, p6, p7;
}
class Value extends LhsPadding
{
    protected volatile long value;
}
class RhsPadding extends Value
{
    protected long p9, p10, p11, p12, p13, p14, p15;
}
/**
 * 1.通过Sequence的一系列的继承关系可以看到, 它真正的用来计数的域是value, 在value的前后各有7个long型的填充值, 这些值在这里的作用是做cpu
 * cache line填充,防止发生伪共享 
 * 2.Sequence类的其他set、get等方法都是通过UNSAFE对象实现对value值的原子操作
 */
public class Sequence extends RhsPadding
{
    static final long INITIAL_VALUE = -1L;
    private static final Unsafe UNSAFE;
    private static final long VALUE_OFFSET;
    static
    {
        UNSAFE = Util.getUnsafe();
        try
        {
            VALUE_OFFSET = UNSAFE.objectFieldOffset(Value.class.getDeclaredField("value"));
        }
        catch (final Exception e)
        {
            throw new RuntimeException(e);
        }
    }
```



- ​	 Sequence 可以看成是AtomicLong 用于标识进度；
- ​    还有另外一个目的就是防止不同Sequence之间CPU缓存伪共享的问题

## 4.4 算法优化-序号栅栏机制

1. 消费者序号数值必须小于生产者序号数值；
2. 消费者序号数值必须小于其前置（依赖关系）消费者的序号数值；
3. 生产者序号数值不能大于消费者中最小的序号数值；？（1/3矛盾？）
4. 以避免生产者速度过快，将还未来得及消费的消息覆盖；
5. Disruptor3.0中，序号栅栏SequenceBarrier和序号Sequence搭配使用，协调和管理消费者与生产者的工作节奏，避免锁和CAS;



**注意**

​	生产者进行投递的时候总是使用；

​	long sequence = ringBuffer.next(); 具体实现参考；



**com.lmax.disruptor.SingleProducerSequencerPad**

```
/**
 * 该方法是事件生产者申请序列
 * 
 * @see Sequencer#next(int)
 */
@Override
public long next(int n)
{
   // 该方法是事件生产者申请序列，n表示此次发布者期望获取多少个序号，通常是1
    if (n < 1 || n > bufferSize)
    {
        throw new IllegalArgumentException("n must be > 0 and < bufferSize");
    }

    // 复制上次成功申请的序列
    long nextValue = this.nextValue;
    // 加上n后，得到本次需要申请的序列
    long nextSequence = nextValue + n;
    // 本次申请的序列减去环形数组的长度，得到绕一圈后的序列
    long wrapPoint = nextSequence - bufferSize;
    // 复制消费者上次消费到的序列位置
    long cachedGatingSequence = this.cachedValue;
    // 如果本次申请的序列，绕一圈后，从消费者后面追上，或者消费者上次消费的序列大于生产者上次申请的序列，则说明发生追尾了，需要进一步处理
    if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
    {
       // wrapPoint > cachedGatingSequence 代表绕一圈并且位置大于消费者处理到的序列
       // cachedGatingSequence > nextValue 说明事件生产者的位置位于消费者的后面
       // 维护父类中事件生产者的序列
        cursor.setVolatile(nextValue);  // StoreLoad fence

        long minSequence;
        // 如果事件生产者绕一圈以后大于消费者的序列，那么会在此处自旋
        while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue)))
        {
            LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
        }
        
        // 循环退出后，将获取的消费者最小序列，赋值给cachedValue
        this.cachedValue = minSequence;
    }

    // 将成功申请到的nextSequence赋值给nextValue
    this.nextValue = nextSequence;

    return nextSequence;
}
```

# 5. WaitStrategy 等待策略分析

**实现类**

1. BlockWaitStrategy

2. BusySpinWaitStrategy

3. LiteBlockingWaitStrategy

4. PhaseBackoffWaitStrategy

5. SleepingWaitStrategy

6. TimeoutBlockingWaitStrategy

7. YeeldingWaitStrategy

   

​    **源码分析部分待续 **







# 6. EventProcesso 核心机制深度分析

**实现类**

1. EventProcessor 实现类： BatchEventProcessor

2. WorkProcessor

   

```
@Override
public void run()
{
     // CAS 操作
    if (running.compareAndSet(IDLE, RUNNING))
    {
        // 序号栅栏进行清空
        sequenceBarrier.clearAlert();
        // 唤醒线程进行工作；
        notifyStart();
        try
        {
            if (running.get() == RUNNING)
            {             
                // 双层循环实现无锁机制；
                processEvents();
            }
        }
        finally
        {
            notifyShutdown();
            running.set(IDLE);
        }
    }
    else
    {
        // This is a little bit of guess work.  The running state could of changed to HALTED by
        // this point.  However, Java does not have compareAndExchange which is the only way
        // to get it exactly correct.
        if (running.get() == RUNNING)
        {
            throw new IllegalStateException("Thread is already running");
        }
        else
        {
            earlyExit();
        }
    }
}
```





```
private void processEvents()
{
    T event = null;
    long nextSequence = sequence.get() + 1L;
    // 双层循环实现无锁机制；
    while (true)
    {
        try
        {
            final long availableSequence = sequenceBarrier.waitFor(nextSequence);
            if (batchStartAware != null && availableSequence >= nextSequence)
            {
                batchStartAware.onBatchStart(availableSequence - nextSequence + 1);
            }

            while (nextSequence <= availableSequence)
            {
                event = dataProvider.get(nextSequence);
                eventHandler.onEvent(event, nextSequence, nextSequence == availableSequence);
                nextSequence++;
            }

            sequence.set(availableSequence);
        }
        catch (final TimeoutException e)
        {
            notifyTimeout(sequence.get());
        }
        catch (final AlertException ex)
        {
            if (running.get() != RUNNING)
            {
                break;
            }
        }
        catch (final Throwable ex)
        {
            exceptionHandler.handleEventException(ex, nextSequence, event);
            sequence.set(nextSequence);
            nextSequence++;
        }
    }
}
```