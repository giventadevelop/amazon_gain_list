package com.amazon.gain.list;



import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Service
public class AmazonListBuilderService {
    private static final Logger logger = LoggerFactory.getLogger(AmazonListBuilderService.class);
    private static final String AMAZON_URL = "https://www.amazon.com";

    private final AmazonProperties amazonProperties;
    private WebDriver driver;
    private WebDriverWait wait;

    private Actions actions;

    public AmazonListBuilderService(AmazonProperties amazonProperties) {
        this.amazonProperties = amazonProperties;
    }

    public void processOrders() {
        try {
            initialize();
            handleCaptcha();
            if (!isPageLoaded()) {
                logger.error("Failed to load Amazon page. Aborting process.");
                //saveScreenshot("failed_load");
                return;
            }
            if (!isLoggedIn()) {
                login();
            } else {
                logger.info("Already logged in. Skipping login process.");
            }

            navigateAndVerify();

            processOrderList();
        } catch (Exception e) {
            logger.error("An error occurred: ", e);
            //saveScreenshot("error_screenshot");
        } finally {
            cleanup();
        }
    }

    private void navigateAndVerify() throws IOException {
        if (isElementClickable(By.id("nav-orders"))) {
            logger.info("Found 'Orders' link. Clicking to navigate.");
            driver.findElement(By.id("nav-orders")).click();

            // Wait for page load after clicking
//            wait.until(ExpectedConditions.urlContains("/order-history"));
            logger.info("Navigated to Orders page.");

            // Wait for a short time to allow any redirects or page loads
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Sleep interrupted", e);
            }

            // Check for verification required
            handleVerification();
            if (isVerificationRequired()) {
                logger.info("Verification required.");
                handleVerification();
            } else {
                logger.info("No verification required. Proceeding with order processing.");
            }
        } else {
            logger.info("'Orders' link not found or not clickable. Checking current page state.");
            if (isVerificationRequired()) {
                logger.info("On verification page.");
                handleVerification();
            } else if (isOnOrdersPage()) {
                logger.info("Already on Orders page. No further navigation needed.");
            } else {
                logger.warn("Unable to determine page state. Please check manually.");
                //saveScreenshot("unknown_page_state");
            }
        }
    }

    private boolean isVerificationRequired() {
        // Check for various possible indicators of a verification page
        return isElementPresent(By.id("ap_phone_number")) ||
                isElementPresent(By.id("auth-verify-button")) ||
                driver.getPageSource().contains("Verify your identity") ||
                driver.getPageSource().contains("Two-Step Verification");
    }

    private void handleVerification() {
        logger.info("Handling verification process");
        //saveScreenshot("verification_page");

        try {
            // Wait for the email/phone input field to be visible
            WebElement inputField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ap_email")));

            // Clear any existing value in the field
            inputField.clear();

            // Enter the phone number
            inputField.sendKeys("3123430073");
            logger.info("Entered phone number: " + "3123430073");

            // Find and click the continue button
            WebElement continueButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[type='submit'], button[type='submit']")));
            clickElement(continueButton);
            logger.info("Clicked continue button");

            // Wait for potential security code input
            handleSecurityCode();

        } catch (TimeoutException e) {
            logger.error("Timed out waiting for verification elements", e);
            //saveScreenshot("verification_timeout");
        } catch (Exception e) {
            logger.error("Error during verification process", e);
            //saveScreenshot("verification_error");
        }
    }

    private WebElement findElement(By... locators) {
        for (By locator : locators) {
            try {
                return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            } catch (TimeoutException e) {
                // Continue to next locator
            }
        }
        return null; // If no element found with any of the locators
    }

    private boolean isElementClickable(By locator) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(locator));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    private boolean isOnOrdersPage() {
        return driver.getCurrentUrl().contains("/order-history") ||
                driver.getCurrentUrl().contains("/orders");
    }


    private void handleCaptcha() throws IOException {
        logger.info("Waiting for potential CAPTCHA...");
        System.out.println("ATTENTION: If you see a CAPTCHA, please solve it now.");
        System.out.println("The program will wait for 30 seconds. Press Enter when you're done or if there's no CAPTCHA.");

        //saveScreenshot("potential_captcha");

        Scanner scanner = new Scanner(System.in);
        long startTime = System.currentTimeMillis();
        long waitTime = 40000; // 30 seconds

        while (System.currentTimeMillis() - startTime < waitTime) {
            if (System.in.available() > 0) {
                scanner.nextLine(); // Consume the Enter key press
                break;
            }
            try {
                Thread.sleep(1000); // Check every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting for CAPTCHA input", e);
            }
        }

        logger.info("Resuming operation after CAPTCHA wait period.");
    }

    private void initialize() throws IOException {
        String chromeDriverPath = setupChromeDriver();
        System.setProperty("webdriver.chrome.driver", chromeDriverPath);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(40));
        wait = new WebDriverWait(driver, Duration.ofSeconds(40));
        actions = new Actions(driver);  // Initialize Actions object
        logger.info("Navigating to Amazon URL: " + AMAZON_URL);
        driver.get(AMAZON_URL);
    }

    private boolean isPageLoaded() {
        try {
            logger.info("Checking if page is loaded...");

            // Wait for the page to be in a ready state
            wait.until((ExpectedCondition<Boolean>) wd ->
                    ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete"));
            logger.info("Document ready state is complete");

            // Check the current URL
            String currentUrl = driver.getCurrentUrl();
            logger.info("Current URL: " + currentUrl);
            if (!currentUrl.contains("amazon.com")) {
                logger.error("Not on Amazon website. Current URL: " + currentUrl);
                return false;
            }

            // Get page title
            String pageTitle = driver.getTitle();
            logger.info("Page title: " + pageTitle);

            // Check page source
            String pageSource = driver.getPageSource();
            logger.info("Page source length: " + pageSource.length());
            if (pageSource.length() < 100) {
                logger.error("Page source is suspiciously short. Possible blank page.");
                return false;
            }

            // Try to find any common elements
            boolean logoFound = isElementPresent(By.id("nav-logo-sprites"));
            boolean searchBarFound = isElementPresent(By.id("twotabsearchtextbox"));
            boolean signInLinkFound = isElementPresent(By.id("nav-link-accountList"));

            logger.info("Common elements found: Logo: " + logoFound +
                    ", Search bar: " + searchBarFound +
                    ", Sign-in link: " + signInLinkFound);

            // If none of the common elements are found, the page might not have loaded correctly
            if (!logoFound && !searchBarFound && !signInLinkFound) {
                logger.error("None of the common Amazon elements found. Page might not have loaded correctly.");
                return false;
            }

            logger.info("Page appears to be loaded successfully");
            return true;
        } catch (Exception e) {
            logger.error("Error while checking if page is loaded", e);
            return false;
        }
    }

    private boolean isElementPresent(By by) {
        try {
            driver.findElement(by);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private void saveScreenshot(String fileName) {
        try {
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), Paths.get(fileName + ".png"), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Screenshot saved: " + fileName + ".png");
        } catch (IOException e) {
            logger.error("Failed to save screenshot", e);
        }
    }

    private void waitForPageLoad() {
        wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
        try {
            Thread.sleep(2000); // Additional 2-second wait for any AJAX calls to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isLoggedIn() {
        try {
            WebElement accountName = driver.findElement(By.id("nav-link-accountList-nav-line-1"));
            return !accountName.getText().contains("Sign in");
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private void login() {
        WebElement signInButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("nav-link-accountList")));
        signInButton.click();

        WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ap_email")));
        emailField.sendKeys(amazonProperties.getUsername());

        WebElement continueButton = driver.findElement(By.id("continue"));
        continueButton.click();

        WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ap_password")));
        passwordField.sendKeys(amazonProperties.getPassword());

        WebElement signInSubmit = driver.findElement(By.id("signInSubmit"));
        signInSubmit.click();

        logger.info("Logged in successfully");
    }

    private void processOrderList() {
        logger.info("Processing order list");
        List<String> years = getAvailableYears();
        for (String year : years) {
            selectYear(year);
            // Process orders for the selected year
            processOrdersForYear(year);
        }
    }
    private List<String> getAvailableYears() {
        List<String> years = new ArrayList<>();
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                // Click to open the dropdown
                WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(By.id("a-autoid-1-announce")));
                dropdown.click();

                // Wait for the dropdown options to be visible
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".a-popover-wrapper .a-dropdown-link")));

                // Find all dropdown options
                List<WebElement> options = driver.findElements(By.cssSelector(".a-popover-wrapper .a-dropdown-link"));

                for (WebElement option : options) {
                    String text = option.getText().trim();
                    if (text.matches("\\d{4}")) {  // Only add if it's a 4-digit year
                        years.add(text);
                    }
                }

                if (!years.isEmpty()) {
                    break;  // Successfully got the years, exit the loop
                }
            } catch (StaleElementReferenceException e) {
                logger.warn("Stale element on attempt " + (attempt + 1) + ", retrying...");
            } catch (Exception e) {
                logger.error("Error getting available years on attempt " + (attempt + 1), e);
            }
        }
        logger.info("Available years: " + years);
        return years;
    }

    private void selectYear1(String year) {
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                // Find and click the dropdown to open it
                WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("span[data-action='a-dropdown-button']")));
                clickElement(dropdown);

                // Wait for the dropdown options to be visible
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector(".a-popover-wrapper .a-dropdown-link")));

                // Find the option for the specified year
                String yearOptionXPath = String.format(
                        "//a[contains(@class, 'a-dropdown-link') and contains(@data-value, 'year-%s')]", year);
                WebElement yearOption = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(yearOptionXPath)));

                // Click the year option
                clickElement(yearOption);

                // Wait for the selection to be applied
                wait.until(ExpectedConditions.textToBePresentInElement(dropdown, year));

                // Wait for any loading indicator to disappear (adjust selector as needed)
                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("a-popover-loading-indicator")));

                logger.info("Selected year: " + year);
                return;  // Successfully selected the year, exit the method
            } catch (StaleElementReferenceException e) {
                logger.warn("Stale element when selecting year " + year + " on attempt " + (attempt + 1) + ", retrying...");
            } catch (ElementClickInterceptedException e) {
                logger.warn("Click intercepted when selecting year " + year + " on attempt " + (attempt + 1) + ", retrying with JavaScript...");
                try {
                    // Try to click using JavaScript
                    String script = String.format(
                            "document.querySelector('a[data-value*=\"year-%s\"]').click();", year);
                    ((JavascriptExecutor) driver).executeScript(script);
                } catch (Exception jsException) {
                    logger.error("JavaScript click failed", jsException);
                }
            } catch (Exception e) {
                logger.error("Error selecting year " + year + " on attempt " + (attempt + 1), e);
                //saveScreenshot("year_selection_error_" + year + "_attempt_" + (attempt + 1));
            }
        }
        logger.error("Failed to select year " + year + " after " + maxAttempts + " attempts");
    }

    private void selectYear(String year) {

        if(Integer.parseInt(year) >2006){
            return;
        }
        int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                // Wait for any loading indicators to disappear
                waitForPageLoad();

                // Find and click the dropdown to open it
                WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("[data-action='a-dropdown-button']")));
                clickElement(dropdown);

                // Wait for the dropdown options to be visible
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector(".a-dropdown-link")));

                // Find the option for the specified year
                WebElement yearOption = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//a[@class='a-dropdown-link' and contains(text(), '" + year + "')]")));

                // Click the year option
                clickElement(yearOption);

                // Wait for the page to reload after selecting the year
                waitForPageLoad();

                // Verify that the year was actually selected
                WebElement selectedYear = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("[data-action='a-dropdown-button'] .a-dropdown-prompt")));
                if (selectedYear.getText().contains(year)) {
                    logger.info("Successfully selected year: " + year);
                    return;  // Year selected successfully, exit the method
                } else {
                    throw new Exception("Year not selected correctly");
                }
            } catch (Exception e) {
                logger.warn("Attempt " + (attempt + 1) + " failed to select year " + year + ": " + e.getMessage());
                if (attempt == maxAttempts - 1) {
                    logger.error("Failed to select year " + year + " after " + maxAttempts + " attempts", e);
                }
            }
        }
    }

    private void clickElement(WebElement element) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element));
            element.click();
        } catch (ElementClickInterceptedException e) {
            logger.warn("Click intercepted, trying with JavaScript...");
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        } catch (Exception e) {
            logger.error("Failed to click element", e);
            throw e;  // Rethrow to be handled by the calling method
        }
    }

    private void navigateToOrdersPage() {
        logger.info("Navigating to Orders page");
        WebElement ordersLink = wait.until(ExpectedConditions.elementToBeClickable(By.id("nav-orders")));
        ordersLink.click();
        wait.until(ExpectedConditions.urlContains("/order-history"));
        logger.info("Navigated to Orders page successfully");
    }

    private void enterPhoneNumber() {
        logger.info("Attempting to enter phone number");
        try {
            WebElement phoneInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ap_phone_number")));
            phoneInput.sendKeys("3123430073");

            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("continue")));
            submitButton.click();

            logger.info("Phone number entered and submitted successfully");
        } catch (TimeoutException e) {
            logger.info("Phone number input not found. It may not be required.");
        } catch (Exception e) {
            logger.error("Error entering phone number", e);
            //saveScreenshot("phone_number_error");
        }
    }

    private void handleSecurityCode() throws IOException {
        logger.info("Waiting for security code entry...");
        System.out.println("ATTENTION: If you see a security code input, please enter it now.");
        System.out.println("The program will wait for 30 seconds. Press Enter when you're done or if no code is required.");

        //saveScreenshot("security_code_page");

        Scanner scanner = new Scanner(System.in);
        long startTime = System.currentTimeMillis();
        long waitTime = 15000; // 30 seconds

        while (System.currentTimeMillis() - startTime < waitTime) {
            if (System.in.available() > 0) {
                scanner.nextLine(); // Consume the Enter key press
                break;
            }
            try {
                Thread.sleep(1000); // Check every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting for security code input", e);
            }
        }

        logger.info("Resuming operation after security code wait period.");
    }

    private void processOrdersForYear(String year) {
        logger.info("Processing orders for year: " + year);
        if(Integer.parseInt(year) >2006){
            return;
        }
        logger.info("Processing orders for year: " + year);
        boolean hasNextPage;
        int pageNumber = 1;

        do {
            logger.info("Processing page " + pageNumber + " for year " + year);

            // Wait for the page to load and find order elements
            List<WebElement> orderElements = waitForOrderElements();

            if (orderElements.isEmpty()) {
                logger.warn("No order elements found on page " + pageNumber + " for year " + year);
                break;
            }

            for (WebElement orderElement : orderElements) {
                try {
                    processOrder(orderElement);
                } catch (StaleElementReferenceException e) {
                    logger.warn("Encountered stale element, retrying...");
                    // Re-find the order elements and retry processing this order
                    orderElements = waitForOrderElements();
                    int index = orderElements.indexOf(orderElement);
                    if (index != -1 && index < orderElements.size()) {
                        processOrder(orderElements.get(index));
                    }
                } catch (Exception e) {
                    logger.error("Error processing order", e);
                }
            }

            hasNextPage = goToNextPage();
            pageNumber++;
        } while (hasNextPage);

        logger.info("Finished processing all orders for year " + year);
    }

    private List<WebElement> waitForOrderElements() {
        try {
            // First, wait for either order elements or the "num-orders" span
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".order-card, .yo-ordercard, [data-test-id='order-card']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".num-orders"))
            ));

            // Check if there are 0 orders
            List<WebElement> numOrdersElements = driver.findElements(By.cssSelector(".num-orders"));
            if (!numOrdersElements.isEmpty()) {
                String numOrdersText = numOrdersElements.get(0).getText();
                if (numOrdersText.contains("0 orders")) {
                    logger.info("No orders found for this period.");
                    return List.of(); // Return empty list
                }
            }

            // If we have orders, find and return them
            List<WebElement> elements = driver.findElements(By.cssSelector(".order-card"));
            if (elements.isEmpty()) {
                elements = driver.findElements(By.cssSelector(".yo-ordercard"));
            }
            if (elements.isEmpty()) {
                elements = driver.findElements(By.cssSelector("[data-test-id='order-card']"));
            }

            if (elements.isEmpty()) {
                logger.warn("No order elements found, but '0 orders' message was not present.");
            } else {
                logger.info("Found " + elements.size() + " order elements.");
            }

            return elements;
        } catch (TimeoutException e) {
            logger.error("Timeout waiting for order elements or '0 orders' message to be present", e);
            return List.of(); // Return empty list if no elements found
        }
    }

    private void processOrder(WebElement orderCard) {
        try {
            List<WebElement> itemLinks = orderCard.findElements(By.cssSelector("div.yohtmlc-product-title"));

            for (WebElement itemLink : itemLinks) {
                // Click on the item link
                clickElement(itemLink);
                if (isSorryPage()) {
                    logger.info("Encountered a 'Sorry' page. Skipping this item.");
                    driver.navigate().back();
//                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-card")));
                    waitForOrderElements();
                    continue;  // Skip to the next item
                }

                // Wait for the page to load
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("add-to-wishlist-button")));

                // Click the "Add to List" button
                WebElement addToListButton = driver.findElement(By.id("add-to-wishlist-button"));
                clickElement(addToListButton);

                // Wait for the wishlist dropdown to appear
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("atwl-list-name-1HWDU7LVZZB5K")));

                // Select the first item in the dropdown list
                WebElement firstWishlistOption = driver.findElement(By.id("atwl-list-name-1HWDU7LVZZB5K"));
                clickElement(firstWishlistOption);

                // Wait for the confirmation popup
//                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".a-popover-wrapper")));

                // Close the popup
//                handleWishlistPopup();

                // Navigate back to the orders page
                driver.navigate().back();

                // Wait for the orders page to load
//                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-card")));
                waitForOrderElements();
                try {
                    // Wait for a few seconds
                    Thread.sleep(3000);  // 5 seconds delay
                    logger.info("Order page wait");
                } catch (InterruptedException e) {
                    logger.error("Order page wait Sleep interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("Error handling Order page wait", e);
                }

                logger.info("Added item to wishlist and returned to orders page");
            }
        } catch (StaleElementReferenceException e) {
            logger.warn("Encountered stale element while processing order, skipping...");
        } catch (Exception e) {
            logger.error("Error processing order", e);
        }
    }

    private boolean isSorryPage() {
        try {
            // Wait a short time for the page to load
            Thread.sleep(1000);

            // Check for an image with "Sorry" in its alt text
            List<WebElement> sorryImages = driver.findElements(By.cssSelector("img[alt*='Sorry' i]"));
            return !sorryImages.isEmpty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for page load", e);
        } catch (Exception e) {
            logger.warn("Error checking for 'Sorry' page", e);
        }
        return false;
    }

    private void handleWishlistPopup() {
        try {
            // Wait for a few seconds
            Thread.sleep(5000);  // 5 seconds delay

            // Find and click the close button
            WebElement closeButton = driver.findElement(By.cssSelector("button[data-action='a-popover-close']"));
            clickElement(closeButton);

            logger.info("Closed wishlist popup");
        } catch (InterruptedException e) {
            logger.error("Sleep interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error handling wishlist popup", e);
        }
    }


    private void closePopup() {
        try {
            // Wait for the close button to be clickable
            WebElement closeButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[data-action='a-popover-close']")));
            clickElement(closeButton);

            // Wait for the popup to disappear
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".a-popover-wrapper")));
        } catch (TimeoutException e) {
            logger.warn("Timeout waiting for popup close button. Attempting alternative close methods.");
            alternativePopupClose();
        } catch (Exception e) {
            logger.error("Error closing popup", e);
        }
    }

    private void alternativePopupClose() {
        try {
            // Try to find any close button
            List<WebElement> closeButtons = driver.findElements(By.cssSelector("button[class*='close'], .a-close-button"));
            if (!closeButtons.isEmpty()) {
                clickElement(closeButtons.get(0));
            } else {
                // If no close button found, try pressing ESC key
                new Actions(driver).sendKeys(Keys.ESCAPE).perform();
            }
            // Wait for the popup to disappear
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".a-popover-wrapper")));
        } catch (Exception e) {
            logger.error("Failed to close popup using alternative methods", e);
        }
    }
    private void processItem(WebElement item) {
        try {
            String itemName = item.findElement(By.cssSelector(".item-title")).getText();
            logger.info("Processing item: " + itemName);

            // Check if the "Add to list" button is present
            WebElement addToListButton = findElement(item, By.cssSelector(".add-to-list-button"));
            if (addToListButton != null) {
                addToListButton.click();
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("wl-huc-post-create-msg")));
                logger.info("Added item to Gain's list: " + itemName);
            } else {
                logger.info("'Add to list' button not found for item: " + itemName);
            }

            // Close any popups that might appear after adding to list
            closePopups();

        } catch (Exception e) {
            logger.error("Error processing item", e);
        }
    }

    private WebElement findElement(WebElement context, By by) {
        try {
            return context.findElement(by);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private void closePopups() {
        List<WebElement> closeButtons = driver.findElements(By.cssSelector(".a-button-close"));
        for (WebElement closeButton : closeButtons) {
            try {
                if (closeButton.isDisplayed()) {
                    closeButton.click();
                    wait.until(ExpectedConditions.invisibilityOf(closeButton));
                }
            } catch (Exception e) {
                logger.warn("Error closing popup", e);
            }
        }
    }

    private void addToGainsList(String itemUrl) {
        driver.get(itemUrl);

        WebElement addToListButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-wishlist-button")));
        addToListButton.click();

        WebElement gainsListOption = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(text(), \"Gain's list\")]")));
        gainsListOption.click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("WLHUC_result")));
        WebElement closeButton = driver.findElement(By.className("a-button-close"));
        closeButton.click();

        logger.info("Added item to Gain's list: " + itemUrl);

        navigateToOrdersPage();
    }

    private String setupChromeDriver() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        String driverFileName = osName.contains("win") ? "chromedriver.exe" : "chromedriver";

        ClassPathResource resource = new ClassPathResource("drivers/" + driverFileName);
        Path tempFile = Files.createTempFile("chromedriver", osName.contains("win") ? ".exe" : "");
        Files.copy(resource.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        tempFile.toFile().setExecutable(true);

        return tempFile.toString();
    }

    private boolean goToNextPage() {
        try {
            WebElement nextButton = driver.findElement(By.cssSelector(".a-pagination .a-last a"));
            if (nextButton.isEnabled()) {
                nextButton.click();
                wait.until(ExpectedConditions.stalenessOf(nextButton));
                return true;
            }
        } catch (NoSuchElementException e) {
            logger.info("No next page button found. Reached the last page.");
        } catch (Exception e) {
            logger.error("Error navigating to next page", e);
        }
        return false;
    }

    private void cleanup() {
        if (driver != null) {
            logger.info("Closing WebDriver");
            driver.quit();
        }
    }
}
