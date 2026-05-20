package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;

import com.sky.vo.UserReportVO;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service

public class ReportServiceImpl implements ReportService {


    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 统计营业额
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        List<Double> turnoverList=new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin=begin.plusDays(1);
            dateList.add(begin);
        }
        for(LocalDate date:dateList){
            LocalDateTime beginTime=LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(date,LocalTime.MAX);
            Map map=new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover=turnover==null?0.0:turnover;
            turnoverList.add(turnover);
        }
        return TurnoverReportVO.builder().
                dateList(StringUtils.join(dateList,",")).
                turnoverList(StringUtils.join(turnoverList,",")).
                build();
    }

    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end之间的每天对应的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin=begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天的新增用户数目
        List<Integer> newUserList=new ArrayList<>();
        //存放每天总的用户数量
        List<Integer> totalUserList=new ArrayList<>();

        for(LocalDate date:dateList){
            LocalDateTime beginTime=LocalDateTime.of(date,LocalTime.MIN);
            LocalDateTime endTime=LocalDateTime.of(date,LocalTime.MAX);
            Map map=new HashMap();
            map.put("end",endTime);
            Integer totalUser=userMapper.countByMap(map);
            map.put("begin",beginTime);
            Integer newUser=userMapper.countByMap(map);
            totalUserList.add(totalUser);
            newUserList.add(newUser);

        }
        //封装结果数据
        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .build();

    }


}
