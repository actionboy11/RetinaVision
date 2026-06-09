package com.example.retinavision.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.pojo.Entity.UserEntity;

public interface UserRegisterMapper extends BaseMapper<UserEntity> {


    default boolean existsByUsername(String  username){
        return selectCount(new QueryWrapper<UserEntity>().eq("username",username))>0;

    }

}
