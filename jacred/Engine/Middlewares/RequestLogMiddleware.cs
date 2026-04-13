using System;
using System.Diagnostics;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Http;

namespace JacRed.Engine.Middlewares
{
    public class RequestLogMiddleware
    {
        private readonly RequestDelegate _next;

        public RequestLogMiddleware(RequestDelegate next)
        {
            _next = next;
        }

        public async Task Invoke(HttpContext context)
        {
            var requestId = context.Request.Headers["x-request-id"].ToString();
            if (string.IsNullOrWhiteSpace(requestId))
                requestId = Guid.NewGuid().ToString();

            context.Response.Headers["x-request-id"] = requestId;

            var sw = Stopwatch.StartNew();
            Exception captured = null;

            try
            {
                await _next(context);
            }
            catch (Exception ex)
            {
                captured = ex;
                throw;
            }
            finally
            {
                sw.Stop();

                var payload = new
                {
                    service = "jacred",
                    @event = "http_request",
                    request_id = requestId,
                    method = context.Request.Method,
                    path = context.Request.Path.Value,
                    query = context.Request.QueryString.Value,
                    status_code = context.Response?.StatusCode ?? 500,
                    duration_ms = sw.Elapsed.TotalMilliseconds,
                    client_ip = context.Request.Headers["x-forwarded-for"].ToString(),
                    error = captured?.Message
                };

                Console.WriteLine(JsonSerializer.Serialize(payload));
            }
        }
    }
}
