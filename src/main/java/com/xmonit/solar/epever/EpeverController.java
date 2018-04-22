package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.Writer;


@RestController()
@RequestMapping("epever")
public class EpeverController {

    @Autowired
    AppConfig appConfig;

    @Autowired
    EpeverService epeverService;


    @RequestMapping("help")
    public void help(Writer respWriter, HttpServletResponse resp) {

        resp.setContentType("text/plain");
        resp.setCharacterEncoding("UTF-8");

        PrintWriter pw = new PrintWriter(respWriter);
        pw.println("USB EPEver Charge Controller client");
        pw.println("Data is available in Prometheus format at /actuator/prometheus");
        pw.println();
    }

    @GetMapping("fields")
    public ResponseEntity fields() throws Exception {
        return fields(".*");

    }

    @GetMapping("fields/{nameFilter}")
    public ResponseEntity fields(@PathVariable String nameFilter) throws Exception {
        return new ResponseEntity(epeverService.findFieldsByNameGroupBySerialPortName(nameFilter),HttpStatus.OK);
    }

    @RequestMapping("")
    public void index(Writer respWriter, HttpServletResponse resp) {
        help(respWriter, resp);
    }

}