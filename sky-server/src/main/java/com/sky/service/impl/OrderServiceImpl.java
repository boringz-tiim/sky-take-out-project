package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.Address;
import org.aspectj.bridge.Message;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.filter.OrderedWebFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import springfox.bean.validators.plugins.schema.DecimalMinMaxAnnotationPlugin;
import springfox.documentation.swagger.readers.operation.OpenApiResponseReader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper ;
    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private DecimalMinMaxAnnotationPlugin decimalMinMaxAnnotationPlugin;

    /**
     * 用户下单
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
        checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());
        Long userId= BaseContext.getCurrentId() ;
        //查询当前用户购物车数据
        ShoppingCart shoppingCart =new ShoppingCart() ;
        shoppingCart.setUserId(userId);
       List<ShoppingCart>shoppingCartsList=shoppingCartMapper.list(shoppingCart);
       if(shoppingCartsList ==null&&shoppingCartsList.size()==0){
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

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单

//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
        JSONObject jsonObject = new JSONObject();
        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        //通过websocket向客户端浏览器推送消息
        Map map=new HashMap();
        map.put("type",1);
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号："+outTradeNo);
        String json=JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }


    /**
     * 用户端历史订单分页查询
     */

    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        PageHelper.startPage(pageNum,pageSize);
        //DTO 前端/业务层传给Mapper查询用的参数对象
        OrdersPageQueryDTO ordersPageQueryDTO =new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);
        //Orders是和数据库表对应的实体类，也就是订单表Orders里面的一条记录
        Page<Orders>page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO>list = new ArrayList();
        //查询处订单明细，并封装到OrderVO进行相应
        //VO返回给前端看的对象，里面还封装了一个订单明细表order_detail
        if(page!=null && page.getTotal()>0){
            for(Orders orders:page){
                Long orderId=orders.getId();
                List<OrderDetail> orderDetails=orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO=new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                orderVO.setOrderDetailList(orderDetails);
                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(),list);
        //PageResult分装两个属性，总记录数和当前页的订单列表
    }

    /**
     * 根据订单Id查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        Orders orders=orderMapper.getById(id);
        List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderId(orders.getId());
        OrderVO orderVO=new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    @Override
    public void userCancelById(Long id) throws Exception {
        //根据id查询订单
        Orders ordersDB=orderMapper.getById(id);
        if(ordersDB==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //订单状态 1待付款 2待接单
        if(ordersDB.getStatus()>2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders=new Orders();
        orders.setId(ordersDB.getId());
        //已经付款，待接单，需要进行退款
        if(ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            log.info("模拟退款成功，订单号：{}", ordersDB.getNumber());

//            weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01)
//            );
//            orders.setPayStatus(Orders.REFUND);
        }
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        //查询当前用户id
        Long userId=BaseContext.getCurrentId();
        //根据订单id查询
        List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderId(id);
        //将订单详情里面的菜品信息重新复制到购物车对象当中
        //Java8 流式写法 List<OrderDetail> List<ShoppingCart>
        List<ShoppingCart>shoppingCartList=orderDetailList.stream().map(x->{
            ShoppingCart shoppingCart = new ShoppingCart();
            //把x里面和shoppingCart同名的属性复制过去，但是不要复制id
            BeanUtils.copyProperties(x,shoppingCart,"id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());


        //将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        //将Orders转换为OrderVO
        List<OrderVO>orderVOList=getOrderVOList(page);
        return new PageResult(page.getTotal(),orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
    //根据状态分别查询2，3，4订单数
        Integer toBeConfirmed=orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed=orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 商家接单
     * @param
     */

    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        //实体对象用于数据库查询
        Orders orders=Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 商家拒单
     * @param ordersRejectionDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        //根据id查询订单
        Orders orderDB=orderMapper.getById(ordersRejectionDTO.getId());

        //订单只有存在且为2（待接单）才可以拒单
        if(orderDB==null||!orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (Orders.PAID.equals(orderDB.getPayStatus())) {
            // 当前未接入真实微信支付，所以这里先模拟退款
            log.info("模拟退款成功，订单号：{}", orderDB.getNumber());

            // 真正接入微信支付后再打开下面代码
            // weChatPayUtil.refund(
            //         ordersDB.getNumber(),
            //         ordersDB.getNumber() + System.currentTimeMillis(),
            //         ordersDB.getAmount(),
            //         ordersDB.getAmount());
        }
        Orders orders=new Orders();
        orders.setId(ordersRejectionDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);

    }

    /**
     * 商家取消订单
     * 所以取消订单和拒单的最大区别就是取消订单可以不用管订单状态
     * @param ordersCancelDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());


        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            // 当前未接入真实微信支付，所以这里先模拟退款
            log.info("模拟退款成功，订单号：{}", ordersDB.getNumber());

            // 真正接入微信支付后再打开下面代码
            // weChatPayUtil.refund(
            //         ordersDB.getNumber(),
            //         ordersDB.getNumber() + System.currentTimeMillis(),
            //         ordersDB.getAmount(),
            //         ordersDB.getAmount());
        }
        Orders orders=new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);

    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders orders=orderMapper.getById(id);
        if(orders==null||!orders.getStatus().equals(Orders.CONFIRMED)){
            throw  new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders1);
    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders orderDB=orderMapper.getById(id);
        if(orderDB==null||!orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = new Orders();
        orders.setId(id);
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(orders);
    }


    public List<OrderVO> getOrderVOList(Page<Orders> page){
        List<OrderVO> orderVOList=new ArrayList<>();
        List<Orders> ordersList=page.getResult();
        if(!CollectionUtils.isEmpty(ordersList)){
            for(Orders orders:ordersList){
                OrderVO orderVO=new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes=getOrderDishesStr(orders);
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }


        }
        return orderVOList;

    }

    /**
     * 根据订单id获取菜品信息字符串
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        //查询订单菜品的详细信息
        List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderId(orders.getId());

        //将每一条订单菜品细腻些拼接为字符串
        List<String> orderDishList=orderDetailList.stream().map(x->{
            String orderDish=x.getName()+"*"+x.getNumber()+";";
            return orderDish;
        }).collect(Collectors.toList());
    return String.join("",orderDishList);
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

}
