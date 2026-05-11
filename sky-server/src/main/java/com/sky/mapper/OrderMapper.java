package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper

public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */

    void insert(Orders orders);

    /**
     * 根据订单好查询订单
     * @param outTradeNo
     * @return
     */
    @Select("select * from orders where number=#{outTradeNo} ")
    Orders getByNumber(String outTradeNo);
    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

}
