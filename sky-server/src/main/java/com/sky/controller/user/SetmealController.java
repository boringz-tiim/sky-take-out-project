package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userSetmealController")
@RequestMapping("user/setmeal")
@Api(tags="c端-套餐浏览接口")

public class SetmealController {
    @Autowired
    private SetmealService setmealService;

    /**
     * 根据分类Id查询套餐
     * @param categoryId
     * @return
     */
    @ApiOperation("根据分类id查询套餐")
    @GetMapping("/list")
    public Result<List<Setmeal>>list(@RequestParam Long categoryId){
        Setmeal setmeal=new Setmeal();
        setmeal.setCategoryId(categoryId );
        setmeal.setStatus(StatusConstant.ENABLE );
//构造一个分类id是categoryId且正在出售的套餐，查询这个套餐并返回套餐列表
        List<Setmeal> list = setmealService.list(setmeal);
        return Result.success(list);
}
    /**
     * 根据套餐id查询包含的菜品
     */
    @ApiOperation("根据套餐Id查询套餐")
    @GetMapping("/dish/{id}")
    public Result<List<DishItemVO >> dishList(@PathVariable("id") Long id){
        List<DishItemVO >list = setmealService.getDishItemById(id);
        return Result.success(list);

    }
}
