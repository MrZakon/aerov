#pragma once
#include <string>
#include <vector>
#include <nlohmann/json.hpp>

namespace aero {

struct HttpResponse {
    int statusCode = 0;
    std::string body;
    std::string error;
    bool isSuccess = false;
    nlohmann::json json;
};

class HttpClient {
public:
    static HttpResponse Get(const std::string& url, const std::string& token = "", int timeoutMs = 10000, bool allowRedirects = true);
    static HttpResponse Post(const std::string& url, const std::string& jsonBody, const std::string& token = "", int timeoutMs = 10000);
    static HttpResponse PostForm(const std::string& url, const std::string& formBody, int timeoutMs = 20000);
    static HttpResponse ApiRequest(const std::string& pathname, const std::string& method = "GET", const std::string& jsonBody = "", const std::string& token = "");

    static std::string DescribeError(int status, const std::string& rawError);
};

} // namespace aero
