package com.yxlogin.service;

import cn.hutool.core.lang.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class UploadFileService {
    private static final String UPLOAD_DIR = "D:/im-files/";
    public String uploadFile(MultipartFile file) {
        if(file==null){
            throw new RuntimeException("文件为空");
        }
        String originalFilename = file.getOriginalFilename();
        String suffix =originalFilename!=null&&originalFilename.contains(".")?
                originalFilename.substring(originalFilename.lastIndexOf(".")):"";
        if(suffix.isEmpty()){
            throw new RuntimeException("文件名不符合");
        }
        String newName= UUID.randomUUID()+suffix;
        File dest = new File(UPLOAD_DIR + newName);
        dest.getParentFile().mkdirs();
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "http://127.0.0.1:8080/files/"+newName;
    }
}
