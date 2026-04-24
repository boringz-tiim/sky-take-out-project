package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@Api(tags="套餐相关接口")
@RequestMapping("/admin/setmeal")
public class SetmealController {
    @Autowired
    private SetmealService setmealService;

    /**
     * 新增套餐
     */
    @PostMapping

    public Result save(@RequestBody  SetmealDTO setmealDTO){
        log.info("新增套餐:{}",setmealDTO);
        setmealService.save(setmealDTO);
        return Result.success();
    }
    /**
     * 套餐分页查询
     */
    @GetMapping("/page")
    @ApiOperation("分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO){
        log.info("套餐分页查询,参数为:{}",setmealPageQueryDTO);
        PageResult pageResult=setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }
    /**
     * 删除套餐
     */

    @DeleteMapping
    @ApiOperation("套餐的批量删除")
    public Result delete(@RequestParam List<Long>ids){
        log.info("套餐的批量删除:{}",ids);
        setmealService.deleteBatch(ids);
        return Result.success() ;
    }
//    /**
//     * 修改菜品
//     * @param dishDTO
//     * @return
//     */
//    @PutMapping
//    @ApiOperation("修改菜品")
//    public Result update(@RequestBody DishDTO dishDTO){
//        log.info("修改菜品:{}",dishDTO);
//        dishService.updateWithFlavor(dishDTO);
//        return Result.success();
//
//    }
    /**
     * 修改套餐
     */
    @PutMapping
    @ApiOperation("修改套餐")
    public Result update(@RequestBody SetmealDTO setmealDTO ){
        log.info("修改套餐:{}",setmealDTO );
        setmealService .updateWithDish(setmealDTO);
        return Result.success();
    }
    /**
     * 根据id查询套餐，用于修改页面回显数据
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐")
    public Result<SetmealVO> grtById(@PathVariable Long id){
        SetmealVO setmealVO =new SetmealVO() ;
        setmealVO =setmealService .getByIdwithdish(id);
        return Result.success(setmealVO);
    }
    /**
     * 停售起售套餐
     */
    @PostMapping("/status/{status}")
    @ApiOperation("套餐起售停售")
    public Result startOrStop(@PathVariable Integer status,Long id){
        setmealService .startOrStop(status,id);
        return Result.success();
    }



}
