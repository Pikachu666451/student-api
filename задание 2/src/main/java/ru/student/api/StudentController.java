package ru.student.api;

import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StudentController {
    @GetMapping(value = "/hello", produces = "text/plain;charset=UTF-8")
    public String hello(@RequestParam(name = "name", defaultValue = "мир") String name) {
        return "Привет, " + name + "!";
    }

    @GetMapping(value = "/sum", produces = MediaType.APPLICATION_JSON_VALUE)
    public BigDecimal sum(@RequestParam("a") BigDecimal a, @RequestParam("b") BigDecimal b) {
        return a.add(b);
    }
}
