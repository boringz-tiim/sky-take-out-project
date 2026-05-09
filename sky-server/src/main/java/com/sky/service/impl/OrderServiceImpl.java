package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.OrderService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.Address;
import org.aspectj.bridge.Message;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springfox.documentation.swagger.readers.operation.OpenApiResponseReader;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService  {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper ;
    @Autowired
    private AddressBookMapper addressBookMapper ;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper ;
    @Autowired
    private OpenApiResponseReader openApiResponseReader;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理业务异常（地址簿为空，购物车数据为空）
        AddressBook addressBook =addressBookMapper.getById(ordersSubmitDTO.getAddressBookId() );
        if(addressBook==null){
            //抛出异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL );
        }
        Long userId= BaseContext.getCurrentId() ;
        //查询当前用户购物车数据
        ShoppingCart shoppingCart =new ShoppingCart() ;
        shoppingCart.setUserId(userId);
       List<ShoppingCart>shoppingCartsList=shoppingCartMapper.list(shoppingCart);
       if(shoppingCartsList ==null&&shoppingCartsList .size()==0){
           throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL );
       }
        //向订单表插入一条数据
        Orders orders =new Orders() ;
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID) ;
        orders.setStatus(Orders.PENDING_PAYMENT );
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone() );
        orders.setConsignee(addressBook.getConsignee() );
        orders.setUserId(userId);
        orders.setAddress(addressBook.getDetail() );
        orderMapper.insert(orders);
        //向订单明细表插入多条数据
        List<OrderDetail>orderDetailList =new ArrayList<>();
        for(ShoppingCart cart:shoppingCartsList){
            OrderDetail orderDetail =new OrderDetail() ;
            BeanUtils.copyProperties(cart,orderDetail );
            orderDetail.setOrderId(orders.getId());//订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //清空购物车数据
        shoppingCartMapper.deleteByUserId(userId);
        //封装VO返回结果
       OrderSubmitVO orderSubmitVO =OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime() )
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;

    }
}
