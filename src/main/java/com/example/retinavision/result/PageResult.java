package com.example.retinavision.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 封装分页查询结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult <T> implements Serializable {  //Serializable 是 Java 序列化的接口，用于对象序列化。

    private List<T> records;
    private long total;
    private Integer pageNo;
    private Integer pageSize;

}
