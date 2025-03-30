package dev.xiushen.manus4j.tool;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.xiushen.manus4j.common.ChromeDriverRunner;
import dev.xiushen.manus4j.common.CommonCache;
import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class BrowserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserService.class);

    private static final String INTERACTIVE_ELEMENTS_SELECTOR = "a, button, input, select, textarea, [role='button'], [role='link']";
    private static final Cache<String, Object> browserCache = CommonCache.browserCache;

    private final ChromeDriverRunner chromeDriverRunner;

    private static final int MAX_LENGTH = 3000;

    public BrowserService(ChromeDriverRunner chromeDriverRunner) {
        this.chromeDriverRunner = chromeDriverRunner;
    }

    private WebDriver getDriver() {
        return chromeDriverRunner.getDriver();
    }

    @Tool(
            name = "navigate",
            description = "Go to a specific URL, use https://baidu.com by default"
    )
    public ToolExecuteResult navigate(
            @ToolParam(description = "URL for 'navigate' or 'newTab' actions") String url) {
        try {
            WebDriver driver = getDriver();
            if (url == null) {
                return new ToolExecuteResult("URL is required for 'navigate' action");
            }
            driver.get(url);
            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Navigated to " + url);
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'navigate' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());
                return new ToolExecuteResult(errorMessage);
            }
            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'navigate' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "click",
            description = "Click an element by index"
    )
    public ToolExecuteResult click(
            @ToolParam(description = "Element index for 'click' or 'inputText' actions") Integer index) {
        try {
            WebDriver driver = getDriver();
            List<WebElement> interactiveElements = getInteractiveElements(driver);

            if (index == null) {
                return new ToolExecuteResult("Index is required for 'click' action");
            }
            if (index < 0 || index >= interactiveElements.size()) {
                return new ToolExecuteResult("Element with index " + index + " not found");
            }

            WebElement element = interactiveElements.get(index);
            element.getText();
            LOGGER.info("Clicking element: {}", element.getText());

            // 记录点击前的窗口状态
            Set<String> beforeWindowHandles = driver.getWindowHandles();
            String currentUrl = driver.getCurrentUrl();

            // 执行点击操作
            simulateHumanBehavior(element);
            try {
                element.click();
            } catch (ElementClickInterceptedException e) {
                // 如果普通点击失败，尝试使用 JavaScript 点击
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].click();", element);
            }

            // 等待页面变化（最多等待10秒）
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            try {
                // 检查是否有新窗口打开
                Set<String> afterWindowHandles = driver.getWindowHandles();
                if (afterWindowHandles.size() > beforeWindowHandles.size()) {
                    // 找出新打开的窗口
                    afterWindowHandles.removeAll(beforeWindowHandles);
                    String newHandle = afterWindowHandles.iterator().next();

                    // 切换到新窗口
                    driver.switchTo().window(newHandle);
                    LOGGER.info("New tab detected, switched to: {}", driver.getCurrentUrl());
                    return new ToolExecuteResult(
                            "Clicked element and opened in new tab: " + driver.getCurrentUrl());
                }

                // 检查URL是否发生变化
                boolean urlChanged = wait.until(d -> !StringUtils.equals(d.getCurrentUrl(), currentUrl));
                if (urlChanged) {
                    LOGGER.info("Page navigated to: {}", driver.getCurrentUrl());
                    return new ToolExecuteResult("Clicked element and navigated to: " + driver.getCurrentUrl());
                }

                // 如果没有明显变化，返回普通点击成功消息
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult("Clicked element at index " + index);
            } catch (TimeoutException e) {
                // 如果超时，检查是否仍在原页面
                if (!StringUtils.equals(driver.getCurrentUrl(), currentUrl)) {
                    return new ToolExecuteResult("Clicked and page changed to: " + driver.getCurrentUrl());
                }
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(
                        "Clicked element at index " + index + " (no visible navigation occurred)");
            }
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'click' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }
            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'click' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "inputText",
            description = "Input text into an element, for 百度(Baidu), the index of the input button is"
    )
    public ToolExecuteResult inputText(
            @ToolParam(description = "Element index for 'click' or 'inputText' actions") Integer index,
            @ToolParam(description = "Text for 'inputText' action") String text) {
        try {
            WebDriver driver = getDriver();
            List<WebElement> interactiveElements = getInteractiveElements(driver);

            if (index == null || text == null) {
                return new ToolExecuteResult("Index and text are required for 'input_text' action");
            }
            if (index < 0 || index >= interactiveElements.size()) {
                return new ToolExecuteResult("Element with index " + index + " not found");
            }
            WebElement inputElement = interactiveElements.get(index);
            if (!inputElement.getTagName().equals("input") && !inputElement.getTagName().equals("textarea")) {
                return new ToolExecuteResult("Element at index " + index + " is not an input element");
            }
            typeWithHumanDelay(inputElement, text);

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Successfully input '" + text + "' into element at index " + index);
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'inputText' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }
            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'inputText' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "keyEnter",
            description = "Hit the Enter key"
    )
    public ToolExecuteResult keyEnter(
            @ToolParam Integer index) {
        try {
            WebDriver driver = getDriver();
            List<WebElement> interactiveElements = getInteractiveElements(driver);

            if (index == null) {
                return new ToolExecuteResult("Index is required for 'key_enter' action");
            }
            if (index < 0 || index >= interactiveElements.size()) {
                return new ToolExecuteResult("Element with index " + index + " not found");
            }
            WebElement enterElement = interactiveElements.get(index);
            enterElement.sendKeys(Keys.RETURN);

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Hit the enter key at index " + index);
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'keyEnter' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }
            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'keyEnter' failed: " + e.getMessage());
        }

    }

    @Tool(
            name = "screenshot",
            description = "Capture a screenshot"
    )
    public ToolExecuteResult screenshot() {
        try {
            WebDriver driver = getDriver();
            TakesScreenshot screenshot = (TakesScreenshot) driver;
            String base64Screenshot = screenshot.getScreenshotAs(OutputType.BASE64);

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult(
                    "Screenshot captured (base64 length: " + base64Screenshot.length() + ")");
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'screenshot' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());

                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'screenshot' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "getHtml",
            description = "Get page HTML content"
    )
    public ToolExecuteResult getHtml() {
        try {
            WebDriver driver = getDriver();
            String html = driver.getPageSource();

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult(
                    html.length() > MAX_LENGTH ? html.substring(0, MAX_LENGTH) + "..." : html);
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'getHtml' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());

                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'getHtml' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "getText",
            description = "Get text content of the page"
    )
    public ToolExecuteResult getText() {
        try {
            WebDriver driver = getDriver();
            String body = driver.findElement(By.tagName("body")).getText();
            LOGGER.info("get_text body is {}", body);

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult(body);
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'getText' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());

                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'getText' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "executeJs",
            description = "Execute JavaScript code"
    )
    public ToolExecuteResult executeJs(
            @ToolParam(description = "JavaScript code for 'executeJs' action") String script) {
        try {
            WebDriver driver = getDriver();
            if (script == null) {
                return new ToolExecuteResult("Script is required for 'execute_js' action");
            }
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            Object result = jsExecutor.executeScript(script);

            browserCache.putAll(getCurrentState());
            if (result == null) {
                return new ToolExecuteResult("Successfully executed JavaScript code.");
            } else {
                return new ToolExecuteResult(result.toString());
            }
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'executeJs' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }
            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'executeJs' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "scroll",
            description = "Scroll the page"
    )
    public ToolExecuteResult scroll(
            @ToolParam(description = "Pixels to scroll (positive for down, negative for up) for 'scroll' action") Integer scrollAmount) {
        try {
            WebDriver driver = getDriver();
            if (scrollAmount == null) {
                return new ToolExecuteResult("Scroll amount is required for 'scroll' action");
            }
            ((JavascriptExecutor) driver).executeScript("window.scrollBy(0," + scrollAmount + ");");
            String direction = scrollAmount > 0 ? "down" : "up";

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Scrolled " + direction + " by " + Math.abs(scrollAmount) + " pixels");
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'scroll' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }
            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'scroll' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "newTab",
            description = "Open a new tab"
    )
    public ToolExecuteResult newTab(
            @ToolParam(description = "URL for 'navigate' or 'new_tab' actions") String url) {
        try {
            WebDriver driver = getDriver();
            if (url == null) {
                return new ToolExecuteResult("URL is required for 'new_tab' action");
            }
            ((JavascriptExecutor) driver).executeScript("window.open('" + url + "', '_blank');");

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Opened new tab with URL " + url);
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'newTab' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());

                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'newTab' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "closeCurrentTab",
            description = "Close the current page"
    )
    public ToolExecuteResult closeCurrentTab(String text) {
        try {
            WebDriver driver = getDriver();
            driver.close();

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Closed current tab");
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'closeCurrentTab' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());

                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'closeCurrentTab' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "switchTab",
            description = "Switch to a specific tab"
    )
    public ToolExecuteResult switchTab(
            @ToolParam(description = "Tab ID for 'switchTab' action") Integer tabId) {
        try {
            WebDriver driver = getDriver();
            if (tabId == null) {
                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult("Tab ID is out of range for 'switch_tab' action");
            }
            Object[] windowHandles = driver.getWindowHandles().toArray();
            driver.switchTo().window(windowHandles[tabId].toString());

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Switched to tab " + tabId);
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'switchTab' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());

                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'switchTab' failed: " + e.getMessage());
        }
    }

    @Tool(
            name = "refresh",
            description = "Refresh the current page"
    )
    public ToolExecuteResult refresh() {
        try {
            WebDriver driver = getDriver();
            driver.navigate().refresh();

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Refreshed current page");
        } catch (Exception e) {
            if (e instanceof ElementNotInteractableException) {
                String errorMessage = String.format("""
                                Browser action 'refresh' failed, mostly like to have used the wrong index argument.
                                You can try to use 'get_html' to get and analyze the page HTML content first and then use other actions to find the right input element.

                                Tips for :
                                1. ignore all the hidden input or textarea elements.
                                2. for baidu engine, you can use js script to do the operation

                                detailed exception message:
                                %s
                                """,
                        e.getMessage());

                browserCache.putAll(getCurrentState());
                return new ToolExecuteResult(errorMessage);
            }

            browserCache.putAll(getCurrentState());
            return new ToolExecuteResult("Browser action 'refresh' failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getCurrentState() {
        WebDriver driver = getDriver();
        Map<String, Object> state = new HashMap<>();

        try {
            // 等待页面加载完成
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(10));

            // 获取基本信息
            String currentUrl = driver.getCurrentUrl();
            String title = driver.getTitle();
            state.put("url", currentUrl);
            state.put("title", title);

            // 获取标签页信息
            Set<String> windowHandles = driver.getWindowHandles();
            List<Map<String, Object>> tabs = new ArrayList<>();
            String currentHandle = driver.getWindowHandle();
            for (String handle : windowHandles) {
                driver.switchTo().window(handle);
                tabs.add(Map.of("url", driver.getCurrentUrl(), "title", driver.getTitle(), "id", handle));
            }
            driver.switchTo().window(currentHandle); // 切回原始标签页
            state.put("tabs", tabs);

            // 获取viewport和滚动信息
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Long scrollTop = (Long) js.executeScript("return window.pageYOffset;");
            Long scrollHeight = (Long) js.executeScript("return document.documentElement.scrollHeight;");
            Long viewportHeight = (Long) js.executeScript("return window.innerHeight;");

            Map<String, Object> scrollInfo = new HashMap<>();
            scrollInfo.put("pixels_above", scrollTop);
            scrollInfo.put("pixels_below", Math.max(0, scrollHeight - (scrollTop + viewportHeight)));
            scrollInfo.put("total_height", scrollHeight);
            scrollInfo.put("viewport_height", viewportHeight);
            state.put("scroll_info", scrollInfo);

            // 获取可交互元素
            String elementsInfo = getInteractiveElementsInfo(driver);
            state.put("interactive_elements", elementsInfo);

            // 捕获截图
            TakesScreenshot screenshot = (TakesScreenshot) driver;
            String base64Screenshot = screenshot.getScreenshotAs(OutputType.BASE64);
            state.put("screenshot", base64Screenshot);

            // 添加帮助信息
            state.put("help", "[0], [1], [2], etc., represent clickable indices corresponding to the elements listed. "
                    + "Clicking on these indices will navigate to or interact with the respective content behind them.");

            return state;

        } catch (Exception e) {
            LOGGER.error("Failed to get browser state", e);
            state.put("error", "Failed to get browser state: " + e.getMessage());
            return state;
        }
    }

    private void simulateHumanBehavior(WebElement element) {
        try {

            // 添加随机延迟
            Thread.sleep(new Random().nextInt(1000) + 500);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void typeWithHumanDelay(WebElement element, String text) {
        simulateHumanBehavior(element);

        // 模拟人类输入速度
        Random random = new Random();
        for (char c : text.toCharArray()) {
            element.sendKeys(String.valueOf(c));
            try {
                Thread.sleep(random.nextInt(100) + 50);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String formatElementInfo(int index, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) getDriver();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) js.executeScript("""
					function getElementInfo(el) {
					    const style = window.getComputedStyle(el);
					    return {
					        tagName: el.tagName.toLowerCase(),
					        type: el.getAttribute('type'),
					        role: el.getAttribute('role'),
					        text: el.textContent.trim(),
					        value: el.value,
					        placeholder: el.getAttribute('placeholder'),
					        name: el.getAttribute('name'),
					        id: el.getAttribute('id'),
					        'aria-label': el.getAttribute('aria-label'),
					        isVisible: (
					            el.offsetWidth > 0 &&
					            el.offsetHeight > 0 &&
					            style.visibility !== 'hidden' &&
					            style.display !== 'none'
					        )
					    };
					}
					return getElementInfo(arguments[0]);
					""", element);

            if (Objects.isNull(props) || !(Boolean) props.get("isVisible")) {
                return "";
            }

            // 构建HTML属性字符串
            StringBuilder attributes = new StringBuilder();

            // 添加基本属性
            if (props.get("type") != null) {
                attributes.append(" type=\"").append(props.get("type")).append("\"");
            }
            if (props.get("role") != null) {
                attributes.append(" role=\"").append(props.get("role")).append("\"");
            }
            if (props.get("placeholder") != null) {
                attributes.append(" placeholder=\"").append(props.get("placeholder")).append("\"");
            }
            if (props.get("name") != null) {
                attributes.append(" name=\"").append(props.get("name")).append("\"");
            }
            if (props.get("id") != null) {
                attributes.append(" id=\"").append(props.get("id")).append("\"");
            }
            if (props.get("aria-label") != null) {
                attributes.append(" aria-label=\"").append(props.get("aria-label")).append("\"");
            }
            if (props.get("value") != null) {
                attributes.append(" value=\"").append(props.get("value")).append("\"");
            }

            String tagName = (String) props.get("tagName");
            String text = (String) props.get("text");

            // 生成标准HTML格式输出
            return String.format("[%d] <%s%s>%s</%s>\n", index, tagName, attributes.toString(), text, tagName);

        } catch (Exception e) {
            LOGGER.warn("格式化元素信息失败 ,应该是页面某些元素过期了， 跳过当前元素格式化: {}", e.getMessage());
            return "";
        }
    }

    // 添加新的方法获取可交互元素
    private List<WebElement> getInteractiveElements(WebDriver driver) {
        return driver.findElements(By.cssSelector(INTERACTIVE_ELEMENTS_SELECTOR))
                .stream()
                .filter(this::isElementVisible)
                .collect(Collectors.toList());
    }

    private String getInteractiveElementsInfo(WebDriver driver) {
        StringBuilder resultInfo = new StringBuilder();
        List<WebElement> interactiveElements = getInteractiveElements(driver);

        for (int i = 0; i < interactiveElements.size(); i++) {
            String formattedInfo = formatElementInfo(i, interactiveElements.get(i));
            if (!formattedInfo.isEmpty()) {
                resultInfo.append(formattedInfo);
            }
        }

        return resultInfo.toString();
    }

    private boolean isElementVisible(WebElement element) {
        try {
            return element.isDisplayed() && element.isEnabled();
        } catch (NoSuchElementException e) {
            return false;
        }
    }
}
