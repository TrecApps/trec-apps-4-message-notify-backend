package com.trecapps.comm.messages.controllers;

import com.trecapps.comm.common.ResponseObj;
import org.springframework.http.ResponseEntity;

public class BaseController {

    protected ResponseEntity<ResponseObj> responseObjToEntity(ResponseObj obj) {
        return new ResponseEntity<>(obj, obj.getHttpStatus());
    }
}
