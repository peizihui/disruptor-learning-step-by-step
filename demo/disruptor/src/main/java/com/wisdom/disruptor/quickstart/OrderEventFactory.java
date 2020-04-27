package com.wisdom.disruptor.quickstart;
import com.lmax.disruptor.EventFactory;

public class OrderEventFactory implements  EventFactory<OrderEvent> {

    @Override
    public OrderEvent newInstance() {
        // 为了返回空的数据对象（Event）
        return new OrderEvent();
    }
}
