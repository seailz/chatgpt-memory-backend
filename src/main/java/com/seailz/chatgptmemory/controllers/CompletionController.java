package com.seailz.chatgptmemory.controllers;

import lol.slz.gptjar.ChatGPT;
import lol.slz.gptjar.model.Function;
import lol.slz.gptjar.model.Message;
import lol.slz.gptjar.model.Param;
import lol.slz.gptjar.request.ChatCompletionRequest;
import lol.slz.gptjar.util.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RestController
public class CompletionController {

    @PostMapping("/completion")
    public ResponseEntity<String> completion(@RequestBody String json) {
        JSONObject obj;
        try {
            obj = new JSONObject(json);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new JSONObject().put("msg", "Invalid JSON payload").toString());
        }

        JSONArray prompt;
        try {
            prompt = obj.getJSONArray("prompt");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new JSONObject().put("msg", "Missing prompt").toString());
        }

        List<Message> messages = new ArrayList<>();
        for (Object o : prompt) {
            try {
                messages.add(Message.fromJson((JSONObject) o));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(new JSONObject().put("msg", "Invalid prompt").toString());
            }
        }

        String apiToken = System.getenv("OPENAI_API_TOKEN");
        if (apiToken == null) return ResponseEntity.internalServerError().body(new JSONObject().put("msg", "Missing OPENAI_API_TOKEN").toString());

        ChatGPT gpt = new ChatGPT(apiToken);
        ChatCompletionRequest request = new ChatCompletionRequest(messages, "gpt-3.5-turbo-0613", gpt);
        request.setFunctionCall("auto");
        messages.add(0, new Message(
                Message.Role.SYSTEM,
                "You may read your memory by calling the function `read_memory`. If you don't know something, then first call `read_memory` and then ask the question again." +
                        " You may also write to your memory by calling the function `write_memory`. Write as much as you can. Anything the user tells you, you should write to your memory."
        ));

        java.util.function.Function<HashMap<String, Object>, HashMap<String, Object>> readMemory = (params) -> {
            File file = new File("memory.txt");
            try {
                if (!file.exists()) file.createNewFile();
                String text = Files.readString(file.toPath());
                ArrayList<String> lines = new ArrayList<>();
                for (String line : text.split("\n")) {
                    if (!line.trim().isEmpty()) lines.add(line);
                }
                HashMap<String, Object> res = new HashMap<>();
                res.put("text", lines);
                return res;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        request.addFunction(new Function(
                "read_memory",
                "Reads your memory and returns a response.",
                readMemory
        ));

        HashMap<Param, Class<?>> writeMemoryParams = new HashMap<>();
        writeMemoryParams.put(new Param("text", "The text to write to your memory."), String.class);
        java.util.function.Function<HashMap<String, Object>, HashMap<String, Object>> writeMemory = (params) -> {
            File file = new File("memory.txt");
            try {
                if (!file.exists()) file.createNewFile();
                String text = Files.readString(file.toPath()) + "\n" + params.get("text");
                Files.writeString(file.toPath(), text);
                HashMap<String, Object> res = new HashMap<>();
                res.put("text", params.get("text"));
                return res;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        request.addFunction(new Function(
                "write_memory",
                "Writes a line of text to your memory.",
                writeMemoryParams,
                writeMemory
        ));

        Response<String> response = request.call();
        response.onError(e -> {
            System.out.println("Error: " + e.getBody());
        });

        String res = response.awaitCompleted();
        return ResponseEntity.ok(new JSONObject().put("completion", res).toString());
    }

}
