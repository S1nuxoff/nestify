using Newtonsoft.Json;
using System;
using System.IO;
using System.IO.Compression;

namespace JacRed.Engine.CORE
{
    public static class JsonStream
    {
        private static string GzipPath(string path) => $"{path}.gz";
        private static string BackupGzipPath(string path) => $"{path}.bak.gz";

        #region Read
        public static T Read<T>(string path)
        {
            var serializer = new JsonSerializer();

            using (Stream file = File.Exists(GzipPath(path)) ? new GZipStream(File.OpenRead(GzipPath(path)), CompressionMode.Decompress) : File.OpenRead(path))
            {
                using (var sr = new StreamReader(file))
                {
                    using (var jsonTextReader = new JsonTextReader(sr))
                    {
                        return serializer.Deserialize<T>(jsonTextReader);
                    }
                }
            }
        }

        public static bool TryRead<T>(string path, out T value, out string sourcePath, out Exception error)
        {
            value = default;
            sourcePath = null;
            error = null;

            foreach (var candidate in new[] { GzipPath(path), path, BackupGzipPath(path) })
            {
                if (!File.Exists(candidate))
                    continue;

                try
                {
                    value = ReadCandidate<T>(candidate);
                    sourcePath = candidate;
                    return true;
                }
                catch (Exception ex)
                {
                    error = ex;
                }
            }

            return false;
        }
        #endregion

        #region Write
        public static void Write(string path, object db)
        {
            var settings = new JsonSerializerSettings()
            {
                Formatting = Formatting.Indented
            };

            var serializer = JsonSerializer.Create(settings);

            var targetPath = GzipPath(path);
            var backupPath = BackupGzipPath(path);
            var tempPath = $"{targetPath}.tmp";
            var directory = Path.GetDirectoryName(targetPath);
            if (!string.IsNullOrWhiteSpace(directory))
                Directory.CreateDirectory(directory);

            using (var sw = new StreamWriter(new GZipStream(File.Create(tempPath), CompressionMode.Compress)))
            {
                using (var jsonTextWriter = new JsonTextWriter(sw))
                {
                    serializer.Serialize(jsonTextWriter, db);
                }
            }

            if (File.Exists(targetPath))
                File.Copy(targetPath, backupPath, true);

            if (File.Exists(targetPath))
                File.Delete(targetPath);

            File.Move(tempPath, targetPath);
        }
        #endregion

        private static T ReadCandidate<T>(string filePath)
        {
            var serializer = new JsonSerializer();
            var isGzip = filePath.EndsWith(".gz", StringComparison.OrdinalIgnoreCase);

            using (Stream file = isGzip ? new GZipStream(File.OpenRead(filePath), CompressionMode.Decompress) : File.OpenRead(filePath))
            using (var sr = new StreamReader(file))
            using (var jsonTextReader = new JsonTextReader(sr))
            {
                return serializer.Deserialize<T>(jsonTextReader);
            }
        }
    }
}
