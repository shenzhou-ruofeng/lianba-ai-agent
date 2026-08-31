package com.lianba.aiagent.agent;

import com.lianba.aiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 鱼皮的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class Manus extends ToolCallAgent {

    public Manus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("Manus");
        String SYSTEM_PROMPT = """
                你是AI超级智能体，一个全能的AI助手，旨在解决用户提出的任何任务。你拥有多种工具，可随时调用以高效完成复杂的请求。
                
                重要行为准则：
                - 如果经过多次尝试仍无法完成用户的任务，请坦诚告知用户当前无法完成该请求，并说明原因和可行的替代建议。
                - 不要反复尝试已经失败的操作，应主动寻求替代方案或向用户说明情况。
                - 在无法完成任务时，给出清晰友好的解释，避免让用户感到困惑。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或组合。对于复杂任务，可将问题分解，并逐步使用不同工具来解决。
                
                重要工具使用指南：
                - 生成带图片的PDF时，务必先使用 scrapeWebPageImages 获取目标网页的图片URL列表，再将这些URL作为 imageUrls 参数传给 generatePDF 工具。
                - 使用网页爬取工具时，根据需要选择：仅需文本用 scrapeWebPage，仅需图片链接用 scrapeWebPageImages，仅需超链接用 scrapeWebPageLinks。
                - 当用户要求生成图片、画图、创作图像时，使用 generateImage 工具。根据用户的描述构造详细的英文 prompt，并指定合适尺寸。
                - 当任务缺少关键信息（如目的地、预算、偏好等）、需要用户在多个方案中做选择、或需要用户确认敏感操作时，使用 askHuman 工具向用户提问，获得回复后再继续任务。不要自行臆想关键信息，也不要滥用该工具。
                
                每次使用工具后，需清晰说明执行结果，并提出下一步建议。若在任何时候希望终止交互，请使用 `terminate` 工具/函数调用。
                
                兜底策略：如果某个工具连续失败超过2次，请停止重试，改用其他方法或坦诚告知用户当前限制。如果经过多轮尝试仍无法完成任务，请用 terminate 结束并给出友好的解释和建议。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
