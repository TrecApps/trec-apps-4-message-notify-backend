package com.trecapps.comm.common;

import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Data
public class ResponseObj {
    transient HttpStatus httpStatus;
    int status;
    String message;
    String id;

    public boolean errorOccurred() {
        this.httpStatus = HttpStatus.valueOf(this.status);
        return !this.httpStatus.isError();
    }

    public static ResponseObj getInstance(String message, String id) {
        ResponseObj ret = new ResponseObj();
        ret.httpStatus = HttpStatus.OK;
        ret.status = ret.httpStatus.value();
        ret.message = message;
        ret.id = id;
        return ret;
    }

    public static ResponseObj getInstance(HttpStatus status, String message) {
        ResponseObj ret = new ResponseObj();
        ret.httpStatus = status;
        ret.status = ret.httpStatus.value();
        ret.message = message;
        return ret;
    }

    public ResponseEntity<ResponseObj> toEntity(){
        return new ResponseEntity<>(this, this.httpStatus);
    }
}
