using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.ResponseCompression;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using System;
using System.Linq;
using System.Text.Json.Serialization;
using JacRed.Engine.Middlewares;

namespace JacRed
{
    public class Startup
    {
        #region Startup
        public IConfiguration Configuration { get; }

        public static IServiceProvider ApplicationServices { get; private set; }

        public Startup(IConfiguration configuration)
        {
            Configuration = configuration;
        }
        #endregion

        #region ConfigureServices
        public void ConfigureServices(IServiceCollection services)
        {
            services.Configure<CookiePolicyOptions>(options =>
            {
                options.CheckConsentNeeded = context => true;
                options.MinimumSameSitePolicy = SameSiteMode.None;
            });

            services.AddResponseCompression(options =>
            {
                options.MimeTypes = ResponseCompressionDefaults.MimeTypes.Concat(new[] { "application/vnd.apple.mpegurl", "image/svg+xml" });
            });

            services.AddControllersWithViews().AddJsonOptions(options => {
                //options.JsonSerializerOptions.IgnoreNullValues = true;
                options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingDefault;
            });
        }
        #endregion


        public void Configure(IApplicationBuilder app, IWebHostEnvironment env, IMemoryCache memoryCache)
        {
            ApplicationServices = app.ApplicationServices;
            if (env.EnvironmentName == "Development")
                app.UseDeveloperExceptionPage();
            else
                app.UseExceptionHandler("/health/error");

            // IP êëèåíòà
            app.UseForwardedHeaders(new ForwardedHeadersOptions
            {
                ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto
            });

            app.UseRouting();
            app.UseResponseCompression();
            app.UseMiddleware<RequestLogMiddleware>();
            app.UseModHeaders();

            app.UseEndpoints(endpoints =>
            {
                endpoints.MapGet("/health", async context =>
                {
                    context.Response.ContentType = "application/json";
                    await context.Response.WriteAsync("{\"status\":\"ok\",\"service\":\"jacred\"}");
                });

                endpoints.MapGet("/health/error", async context =>
                {
                    context.Response.StatusCode = 500;
                    context.Response.ContentType = "application/json";
                    await context.Response.WriteAsync("{\"status\":\"error\",\"service\":\"jacred\"}");
                });

                endpoints.MapControllers();
            });
        }
    }
}
