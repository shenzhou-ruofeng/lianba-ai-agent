package com.lianba.aiagent.agent;

import com.lianba.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    // 记录上一步的错误信息，用于状态异常时提供上下文
    private transient String lastErrorMessage;

    // 记录最后一次 think 的文本内容，用于前端展示
    private transient String lastThoughtText;

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // 如果已处于错误状态，返回具体错误信息
                if (getState() == AgentState.ERROR) {
                    String errorMsg = lastErrorMessage != null ? lastErrorMessage : "未知错误";
                    return "思考过程遇到错误：" + errorMsg;
                }
                // 无需执行行动，代理已回答完毕，结束循环
                setState(AgentState.FINISHED);
                return "思考完成 - 无需行动";
            }
            // 再行动
            return act();
        } catch (Exception e) {
            // 记录异常日志
            setState(AgentState.ERROR);
            lastErrorMessage = e.getMessage();
            e.printStackTrace();
            return "步骤执行失败：" + e.getMessage();
        }
    }

}
