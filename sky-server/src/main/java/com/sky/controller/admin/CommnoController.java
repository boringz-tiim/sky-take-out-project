package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags="通用接口")
@Slf4j
public class CommnoController {
    @Autowired
    private AliOssUtil aliOssUtil;
    @Autowired
    private StringHttpMessageConverter stringHttpMessageConverter;

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file)  {
        try {
            log.info("文件上传:{}",file);
            String originalFilename=file.getOriginalFilename();
            //获取原始文件名的后缀
            String extension=originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName= UUID.randomUUID().toString()+extension;
            String filePath=  aliOssUtil.upload(file.getBytes(), objectName);
            //吧文件上传到阿里云
            return Result.success(filePath);
        } catch (IOException e) {
            log.error("文件上传失败:{}",e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}
