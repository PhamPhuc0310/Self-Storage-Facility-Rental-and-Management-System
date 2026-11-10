import java.sql.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Optional synthetic local accounts. Never imports customer/payment history. */
public class CreateLocalUsers {
    static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name);
        return value;
    }
    static void execute(Connection c, String sql, String... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setString(i + 1, args[i]);
            if (s.executeUpdate() != 1) throw new SQLException("Expected one local setup row");
        }
    }
    public static void main(String[] args) throws Exception {
        String password = env("SAFEBOX_LOCAL_PASSWORD");
        if (password.length() < 16) throw new IllegalArgumentException("Use a new local password of at least16 characters");
        try (var c = DriverManager.getConnection(env("DB_URL"), env("DB_USERNAME"), env("DB_PASSWORD"))) {
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT DB_NAME(),(SELECT COUNT(*) FROM dbo.USERS)")) {
                r.next();
                if (!r.getString(1).matches("SafeBox_Local(?:_[A-Za-z0-9]+)?") || r.getInt(2) != 0)
                    throw new IllegalStateException("Requires a bootstrapped SafeBox_Local database with no users; nothing overwritten");
            }
            c.setAutoCommit(false);
            try {
                String admin = UUID.randomUUID().toString(), facility = UUID.randomUUID().toString();
                String[][] roles = {{"admin", "SYSTEM_ADMIN"}, {"customer", "CUSTOMER"},
                        {"staff", "FACILITY_STAFF"}, {"manager", "FACILITY_MANAGER"},
                        {"business", "BUSINESS_OPERATIONS_MANAGER"}};
                var encoder = new BCryptPasswordEncoder();
                for (var role : roles) {
                    String id = role[0].equals("admin") ? admin : UUID.randomUUID().toString();
                    execute(c, "INSERT dbo.USERS(user_id,role_id,email,password_hash,full_name,phone,status,email_verified) "
                            + "SELECT ?,role_id,?,?,?,'0000000000','ACTIVE',1 FROM dbo.ROLES WHERE role_name=?",
                            id, role[0] + "@local.safebox.test", encoder.encode(password), "Local " + role[0], role[1]);
                    if (role[0].equals("admin")) {
                        execute(c, "INSERT dbo.FACILITIES(facility_id,name,address,status,facility_code,region_code,opening_time,closing_time) "
                                + "VALUES(?,'Local SafeBox','Synthetic local setup only','ACTIVE','LOCAL','LOCAL','08:00','18:00')", facility);
                    }
                    if (Set.of("staff", "manager", "business").contains(role[0]))
                        execute(c, "INSERT dbo.USER_FACILITY_ASSIGNMENTS(user_id,facility_id,assigned_by,start_date,status) "
                                + "VALUES(?,?,?,CAST(GETDATE() AS date),'ACTIVE')", id, facility, admin);
                    if (role[0].equals("staff"))
                        execute(c, "INSERT dbo.STAFF_REGION_ASSIGNMENTS(assignment_id,user_id,region_code,status,assigned_by) "
                                + "VALUES(NEWID(),?,'LOCAL','ACTIVE',?)", id, admin);
                }
                c.commit();
                System.out.println("Created five local.safebox.test accounts and one synthetic facility; use your supplied password. No contracts/payments/policies created.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
}
