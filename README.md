# Amazon List Builder
https://claude.ai/chat/f6865f4d-0c08-4cb9-8cdb-d7f2fe1506f0

## Overview
Amazon List Builder is a Java application that automates the process of adding items from your Amazon order history to a specified wishlist. It uses Selenium WebDriver to interact with the Amazon website, navigating through your order history and adding items to your designated list.

## Features
- Automatically navigates through Amazon order history
- Processes orders year by year
- Adds items to a specified Amazon wishlist
- Handles various page structures and edge cases (e.g., years with no orders, "Sorry" pages)
- Provides detailed logging for monitoring and debugging

## Prerequisites
- Java JDK 11 or higher
- Maven
- Chrome browser
- ChromeDriver (compatible with your Chrome version)

## Setup
1. Clone the repository:
   ```
   git clone https://github.com/yourusername/amazon-list-builder.git
   ```

2. Navigate to the project directory:
   ```
   cd amazon-list-builder
   ```

3. Update the `application.properties` file with your Amazon credentials:
   ```
   amazon.username=your_username
   amazon.password=your_password
   ```

4. Ensure that ChromeDriver is in your system PATH or update the path in the code.

## Building the Project
To build the project, run the following command in the project root directory:
```
mvn clean package
```

## Running the Application
After building, you can run the application using:
```
java -jar target/amazon-list-builder-1.0-SNAPSHOT.jar
```

## Configuration
You can modify the following in the `AmazonListBuilderService` class:
- `AMAZON_URL`: The base URL for Amazon (default is "https://www.amazon.com")
- Adjust wait times and retry attempts in various methods if needed

## Troubleshooting
- If you encounter "Element not found" errors, check if the CSS selectors or XPaths need updating due to changes in Amazon's page structure.
- For "Stale Element Reference" exceptions, the application includes retry mechanisms, but you may need to adjust wait times.
- Ensure your internet connection is stable while running the application.

## Limitations
- This tool is dependent on Amazon's current web structure. Changes to Amazon's website may require updates to the code.
- Excessive use may violate Amazon's terms of service. Use responsibly and at your own risk.

## Contributing
Contributions to improve the Amazon List Builder are welcome. Please feel free to submit pull requests or create issues for bugs and feature requests.

## Disclaimer
This project is for educational purposes only. Be sure to comply with Amazon's terms of service and use this tool responsibly.

## License
[Insert your chosen license here]

