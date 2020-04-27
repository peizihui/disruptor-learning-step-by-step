package com.wisdom.disruptor.quickstart;
import com.lmax.disruptor.EventHandler;

/**
 *  具体消费者，
 */
public class OrderEventHandler implements  EventHandler<OrderEvent> {
    /**
     *事件驱动时间，
     */
    @Override
    public void onEvent(OrderEvent orderEvent, long l, boolean b) throws Exception {
        System.out.println("OrderEventHandler.onEvent value =="+orderEvent.getValue());
    }
}
