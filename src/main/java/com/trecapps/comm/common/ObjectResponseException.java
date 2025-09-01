package com.trecapps.comm.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ObjectResponseException extends RuntimeException {
    @Getter
    HttpStatus status;
    String message;

    public ObjectResponseException(HttpStatus status1, String message){
        super(message);
        this.message = message;
        this.status = status1;
    }

    public ResponseObj toResponseObj(){
        return ResponseObj.getInstance(status,message);
    }

    public ResponseEntity<ResponseObj> toResponseEntity(){
        return toResponseObj().toEntity();
    }
}
