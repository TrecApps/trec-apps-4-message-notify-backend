package com.trecapps.comm.messages.services;

import com.trecapps.auth.common.models.TcBrands;
import com.trecapps.auth.common.models.TrecAuthentication;
import reactor.core.publisher.Mono;

public class ProfileSorterService {

    protected Mono<String> useProfile(TrecAuthentication authentication){
        return Mono.just(authentication)
                .map((TrecAuthentication auth) -> {
                    TcBrands brand = auth.getBrand();
                    return brand == null ?
                            String.format("User-%s", auth.getUser().getId()) :
                            String.format("Brand-%s", brand.getId());
                });
    }
}
