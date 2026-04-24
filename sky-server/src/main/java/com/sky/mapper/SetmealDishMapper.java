package com.sky.mapper;

import com.sky.entity.SetmealDish;
import io.swagger.annotations.ApiOperation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealDishMapper {
    /**
     * 根据菜品id查询对应的套餐id
     * @param dishIds
     * @return
     */

    List<Long> getSetmealIdsByDishIds(List<Long> dishIds);

    /**
     * 批量保存套餐和菜品的关联关系
     * @param dishes
     */
    void insertBatch(List<SetmealDish> dishes);

    /**
     * 根据Id删除关联的菜品信息
     * @param ids
     */
    void deleteByIds(List<Long> mealids);
@Delete("delete from setmeal_dish where setmeal_id=#{id}")
    void deleteById(Long id);

    /**
     * 根据套餐id查询套餐和菜品的关联关系
     * @param id
     * @return
     */
    @Select("select * from setmeal_dish where setmeal_id=#{id}")
    List<SetmealDish> getDishBysetmealId(Long id);
}
