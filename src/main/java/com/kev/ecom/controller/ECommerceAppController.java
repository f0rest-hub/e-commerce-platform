package com.kev.ecom.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ECommerceAppController {
    @GetMapping("/hello-world")
    public String helloWorld(){
        return "Hello World";
    }
}
