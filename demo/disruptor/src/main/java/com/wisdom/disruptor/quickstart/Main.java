package com.wisdom.disruptor.quickstart;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        //1. 实例化disruptor对象；
        OrderEventFactory orderEventFactory = new OrderEventFactory();

        //2 ringBufferSize
        int ringBufferSize = 1024 * 1024;
        // 3. 线程池；
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        // ProducerType.SINGLE;
        // ProducerType.MULTI;

        /**
         * 1.eventFactory :消息（event）工厂对象；
         * 2.ringBufferSize: 容器的长度；
         * 3. executor :线程池（自定义最优）；
         * 4.ProducerType:单生产者还是多生产者；
         * 5.waitStrategy:等待策略；
         */

         Disruptor<OrderEvent> disruptor = new Disruptor<>(orderEventFactory,ringBufferSize,executor, ProducerType.SINGLE,new BlockingWaitStrategy());
         // 2.添加消费者的监听（构建disruptor 与消费者的一个关联关系）
         disruptor.handleEventsWith(new OrderEventHandler());
         // 3. 启动disruptor;
        disruptor.start();
         //  4. 获取实际存储数据的容器；RingBuffer
        RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
        OrderEventProducer producer = new OrderEventProducer(ringBuffer);
        ByteBuffer bb = ByteBuffer.allocate(8);
        for(long i =0 ;i < 100; i++){
            bb.putLong(0,i);
            producer.sendData(bb);
        }
        disruptor.shutdown();
        executor.shutdown();

    }
}
