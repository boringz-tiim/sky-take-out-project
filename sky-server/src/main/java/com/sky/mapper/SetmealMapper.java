package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
//注意mapper是接口！！
public interface SetmealMapper {
    /**
     * 新增套餐
     * @param setmeal
     */
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    @Insert("insert into setmeal (category_id, name, price, status, description, image, create_time, update_time, create_user, update_user) values " +
            "(#{categoryId},#{name},#{price},#{status},#{description},#{image},#{createTime},#{updateTime},#{createUser},#{updateUser})")
    @AutoFill(value = OperationType.INSERT)
    void saves(Setmeal setmeal);

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */

    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据id查询套餐
     * @param id
     * @return
     */
    @Select("select * from setmeal where id=#{id}")
    Setmeal getById(Long id);

    /**
     * 根据id集合批量删除套餐
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * 更新套餐
     * @param setmeal
     */
    //@AutoFill(value=OperationType.UPDATE)
    @AutoFill(value = OperationType.UPDATE)
    void update(Setmeal setmeal);
}
