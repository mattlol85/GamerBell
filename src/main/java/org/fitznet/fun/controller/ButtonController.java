package org.fitznet.fun.controller;

/*
    i want to make a hardware button and led and distribute it to all my friends. the board will have wifi.
 */

import org.fitznet.fun.dto.ButtonRequest;
import org.fitznet.fun.service.ButtonService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/button")
public class ButtonController {

    final
    ButtonService buttonService;

    public ButtonController(ButtonService buttonService) {
        this.buttonService = buttonService;
    }

    @PostMapping
    public void buttonEvent(ButtonRequest request) {
        // send a message to all friends
    }

}
