package com.yxlogin.R;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class Result<T>{
    private Integer code;
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;
    public static <T> Result<T> success(){
        return success(null,"成功");
    }
    public static <T> Result<T> success(T data,String message){
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setData(data);
        r.setMessage(message);
        return r;
    }
    public static <T> Result<T> fail(){
        return fail("执行失败",500);
    }
    public static <T> Result<T> fail(String message,Integer code){
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
