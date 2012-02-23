package archimulator.sim.os;

import org.jruby.ext.posix.*;
import org.jruby.ext.posix.util.Platform;

import java.io.FileDescriptor;

public final class EnhancedLinuxPOSIX extends BaseNativePOSIX {
    private final boolean hasFxstat;
    private final boolean hasLxstat;
    private final boolean hasXstat;
    private final boolean hasFstat;
    private final boolean hasLstat;
    private final boolean hasStat;
    private final int statVersion;

    public EnhancedLinuxPOSIX(String libraryName, LibC libc, POSIXHandler handler) {
        super(libraryName, libc, handler);

        statVersion = Platform.IS_32_BIT ? 3 : 0;

        /*
         * Most linux systems define stat/lstat/fstat as macros which force
         * us to call these weird signature versions.
         */
        hasFxstat = hasMethod("__fxstat64");
        hasLxstat = hasMethod("__lxstat64");
        hasXstat = hasMethod("__xstat64");

        /*
        * At least one person is using uLibc on linux which has real
        * definitions for stat/lstat/fstat.
        */
        hasFstat = !hasFxstat && hasMethod("fstat64");
        hasLstat = !hasLxstat && hasMethod("lstat64");
        hasStat = !hasXstat && hasMethod("stat64");
    }

    @Override
    public FileStat allocateStat() {
        if (Platform.IS_32_BIT) {
            return new LinuxHeapFileStat(this);
        } else {
            return new Linux64HeapFileStat(this);
        }
    }

    @Override
    public FileStat fstat(FileDescriptor fileDescriptor) {
        if (!hasFxstat) {
            if (hasFstat) return super.fstat(fileDescriptor);

            handler.unimplementedError("fstat");
        }

        FileStat stat = allocateStat();
        int fd = helper.getfd(fileDescriptor);

        if (((LinuxLibC) libc).__fxstat64(statVersion, fd, stat) < 0) handler.error(ERRORS.ENOENT, "" + fd);

        return stat;
    }

    public FileStat fstat(int fd) {
        if (!hasFxstat) {
            throw new UnsupportedOperationException();
        }

        FileStat stat = allocateStat();

        if (((LinuxLibC) libc).__fxstat64(statVersion, fd, stat) < 0) handler.error(ERRORS.ENOENT, "" + fd);

        return stat;
    }
}
