package meghanada.cache;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import meghanada.analyze.CompileResult;
import meghanada.analyze.Source;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;


public class JavaSourceLoader extends CacheLoader<File, Source> implements RemovalListener<File, Source> {

    private static final Logger log = LogManager.getLogger(JavaSourceLoader.class);

    private Project project;

    public JavaSourceLoader(final Project project) {
        this.project = project;
    }

    public Source writeSourceCache(final Source source) throws IOException {
        final File sourceFile = source.getFile();
        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir);
        final String path = FileUtils.toHashedPath(sourceFile, GlobalCache.CACHE_EXT);
        final String out = Joiner.on(File.separator).join(GlobalCache.SOURCE_CACHE_DIR, path);
        final File file = new File(root, out);

        file.getParentFile().mkdirs();

        log.debug("write file:{}", file);
        GlobalCache.getInstance().asyncWriteCache(file, source);
        return source;
    }

    public Source removeSourceCache(final Source source) throws IOException {
        final File sourceFile = source.getFile();
        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir);
        final String path = FileUtils.toHashedPath(sourceFile, GlobalCache.CACHE_EXT);
        final String out = Joiner.on(File.separator).join(GlobalCache.SOURCE_CACHE_DIR, path);
        final File file = new File(root, out);
        file.delete();
        return source;
    }

    private Optional<Source> loadFromCache(final File sourceFile) throws IOException {

        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir);
        final String path = FileUtils.toHashedPath(sourceFile, GlobalCache.CACHE_EXT);
        final String out = Joiner.on(File.separator).join(GlobalCache.SOURCE_CACHE_DIR, path);
        final File file = new File(root, out);

        if (!file.exists()) {
            return Optional.empty();
        }

        log.debug("load file:{}", file);
        final Source source = GlobalCache.getInstance().readCacheFromFile(file, Source.class);
        return Optional.ofNullable(source);
    }

    @Override
    public Source load(final File file) throws IOException {
        final Config config = Config.load();

        if (!config.useSourceCache()) {
            final CompileResult compileResult = project.parseFile(file);
            return compileResult.getSources().get(file);
        }

        final File checksumFile = FileUtils.getProjectDataFile(GlobalCache.SOURCE_CHECKSUM_DATA);
        final Map<String, String> finalChecksumMap = config.getChecksumMap(checksumFile);

        final String path = file.getCanonicalPath();
        final String md5sum = FileUtils.md5sum(file);
        if (finalChecksumMap.containsKey(path)) {
            // compare checksum
            final String prevSum = finalChecksumMap.get(path);
            if (md5sum.equals(prevSum)) {
                // not modify
                // load from cache
                try {
                    final Optional<Source> source = this.loadFromCache(file);
                    if (source.isPresent()) {
                        return source.get();
                    }
                } catch (Exception e) {
                    log.catching(e);
                }
            }
        }

        final CompileResult compileResult = project.parseFile(file.getCanonicalFile());
        return compileResult.getSources().get(file.getCanonicalFile());
    }

    @Override
    public void onRemoval(RemovalNotification<File, Source> notification) {
        final RemovalCause cause = notification.getCause();

        final Config config = Config.load();
        if (config.useSourceCache()) {
            if (cause.equals(RemovalCause.EXPIRED) ||
                    cause.equals(RemovalCause.SIZE) ||
                    cause.equals(RemovalCause.REPLACED)) {
                final Source source = notification.getValue();
                try {
                    writeSourceCache(source);
                } catch (Exception e) {
                    log.catching(e);
                }
            } else if (cause.equals(RemovalCause.EXPLICIT)) {
                final Source source = notification.getValue();
                try {
                    removeSourceCache(source);
                } catch (Exception e) {
                    log.catching(e);
                }
            }
        }
    }
}