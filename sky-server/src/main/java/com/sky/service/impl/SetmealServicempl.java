package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.bridge.Message;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServicempl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐分类
     * @param setmealDTO
     */
    @Override
    public void save(SetmealDTO setmealDTO) {
        Setmeal setmeal=new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmeal.setStatus(StatusConstant.DISABLE);
        //向套餐表中插入一条数据
        setmealMapper.saves(setmeal);

        List<SetmealDish> dishes =setmealDTO.getSetmealDishes();//拿到菜品集合
        Long mealId=setmeal.getId();
        if(dishes!=null && dishes.size()>0){
            //向套餐表插入数据
            dishes.forEach(dish->{
                dish.setSetmealId(mealId);
            });
            setmealDishMapper.insertBatch(dishes);
        }
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page=setmealMapper.pageQuery(setmealPageQueryDTO);
        long total=page.getTotal();
        List<SetmealVO> records=page.getResult();
        return new PageResult(total,records);
    }

    /**
     * 套餐的批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        //判断当前套餐是否可以删除--是否存在正在售卖中的套餐？
        for(Long id:ids){
            Setmeal setmeal = setmealMapper .getById(id);
            if(setmeal.getStatus() == StatusConstant .ENABLE ){
                throw new DeletionNotAllowedException(MessageConstant .SETMEAL_ON_SALE);


            }
        }
        //根据套餐id结合批量删除菜品数据
        setmealMapper.deleteByIds(ids);
        //根据套餐id集合批量删除关联的菜品数据
        setmealDishMapper.deleteByIds(ids);
    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Override
    public void updateWithDish(SetmealDTO setmealDTO) {
            //菜品全部删除重新插入一遍数据
            //修改套餐基本信息
            Setmeal setmeal = new Setmeal() ;
            BeanUtils.copyProperties(setmealDTO ,setmeal);
            setmealMapper .update(setmeal);
            //删除所有相关的菜品数据
           setmealDishMapper .deleteById(setmeal.getId()) ;

           //重新插入关联的菜品信息
        List<SetmealDish>setmealDishes =setmealDTO.getSetmealDishes() ;
        if(setmealDishes!=null && setmealDishes.size()>0){
            setmealDishes.forEach(setmealDish ->{
                setmealDish.setSetmealId(setmealDTO.getId() ) ;
            });
            setmealDishMapper.insertBatch(setmealDishes );
        }
    }

    @Override
    public SetmealVO getByIdwithdish(Long id) {
        Setmeal setmeal =setmealMapper.getById(id);
        List<SetmealDish> setmealDishes=setmealDishMapper .getDishBysetmealId(id);
       SetmealVO setmealVO =new SetmealVO() ;
       BeanUtils.copyProperties(setmeal,setmealVO);
       setmealVO.setSetmealDishes(setmealDishes );
       return setmealVO ;

    }

    /**
     * 套餐的起售与停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //起售套餐时候，要判断是否有停售菜品，有停售菜品要提示"套餐内包含未起售菜品，无法启售"
        if(status==StatusConstant .ENABLE ){
            List<Dish> dishList=dishMapper.getBySetmealId(id);
            if(dishList!=null && dishList.size()>0){
                dishList.forEach(dish ->{
                    if(StatusConstant .DISABLE ==dish.getStatus() ){
                        throw new SetmealEnableFailedException(MessageConstant .SETMEAL_ENABLE_FAILED );
                    }
                }) ;
            }
        }
        Setmeal setmeal =Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper .update(setmeal);
    }
}
