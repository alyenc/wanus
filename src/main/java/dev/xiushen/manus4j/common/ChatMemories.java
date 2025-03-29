package dev.xiushen.manus4j.common;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;

public class ChatMemories {
    public static final ChatMemory planningMemory = new InMemoryChatMemory();
    public static final ChatMemory memory = new InMemoryChatMemory();
    public static final ChatMemory finalizeMemory = new InMemoryChatMemory();
}
