package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
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

    /**
     * 订单分页条件查询并且按照下单时间排序
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据Id查询订单信息
     * @param id
     * @return
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);

    /**
     * 根据状态统计订单数量
     * @param
     * @return
     */
    @Select("select count(id) from orders where status=#{status}")
    Integer countStatus(Integer status);
}
