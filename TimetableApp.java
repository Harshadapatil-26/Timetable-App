import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;

public class TimetableApp {
    private JFrame frame;
    private JTable table;
    private DefaultTableModel model;
    private JComboBox<String> classBox, subjectBox, dayBox, teacherBox, timeSlotBox;
    private java.util.List<String> timeSlots = new ArrayList<>();
    private static final String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};

    private Connection conn;

    private static final Map<String, String[]> teacherSubjects = new HashMap<>();
    static {
        teacherSubjects.put("John", new String[]{"Physics", "Chemistry"});
        teacherSubjects.put("Mary", new String[]{"Maths", "English"});
        teacherSubjects.put("Harshada", new String[]{"Biology", "Computer Science"});
    }

    public TimetableApp() {
        // Database connection setup
        connectToDatabase();
        createTableIfNotExists();

        frame = new JFrame("Timetable Generator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);
        frame.setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(9, 2));
        classBox = new JComboBox<>(new String[]{"Class 11", "Class 12"});
        teacherBox = new JComboBox<>(teacherSubjects.keySet().toArray(new String[0]));
        subjectBox = new JComboBox<>();
        dayBox = new JComboBox<>(days);

        generateTimeSlots();
        timeSlotBox = new JComboBox<>(timeSlots.toArray(new String[0]));

        JButton addButton = new JButton("Add Slot");
        JButton deleteButton = new JButton("Delete Slot");
        JButton showSlotsButton = new JButton("Show Time Slots");

        addButton.addActionListener(e -> addTimeSlot());
        deleteButton.addActionListener(e -> deleteTimeSlot());
        showSlotsButton.addActionListener(e -> showTimeSlots());

        inputPanel.add(new JLabel("Class:"));
        inputPanel.add(classBox);
        inputPanel.add(new JLabel("Teacher:"));
        inputPanel.add(teacherBox);
        inputPanel.add(new JLabel("Subject:"));
        inputPanel.add(subjectBox);
        inputPanel.add(new JLabel("Day:"));
        inputPanel.add(dayBox);
        inputPanel.add(new JLabel("Time Slot:"));
        inputPanel.add(timeSlotBox);
        inputPanel.add(addButton);
        inputPanel.add(deleteButton);
        inputPanel.add(new JLabel("Show Slots for Teacher:"));
        inputPanel.add(showSlotsButton);

        frame.add(inputPanel, BorderLayout.NORTH);

        model = new DefaultTableModel(new String[]{"Day", "Time", "Teacher", "Subject"}, 0);
        table = new JTable(model);
        frame.add(new JScrollPane(table), BorderLayout.CENTER);

        teacherBox.addActionListener(e -> updateSubjectList());
        updateSubjectList();

        loadExistingData();
        frame.setVisible(true);
    }

    private void connectToDatabase() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:timetable.db");
            System.out.println("Connected to SQLite database.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTableIfNotExists() {
        try (Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS timetable (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                         "day TEXT, " +
                         "time TEXT, " +
                         "teacher TEXT, " +
                         "subject TEXT)";
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadExistingData() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM timetable")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("day"),
                    rs.getString("time"),
                    rs.getString("teacher"),
                    rs.getString("subject")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void generateTimeSlots() {
        int hour = 8, minute = 0;
        for (int i = 0; i < 4; i++) {
            timeSlots.add(formatTime(hour, minute) + " - " + formatTime(hour, minute + 50));
            minute += 50;
            if (minute >= 60) {
                minute -= 60;
                hour++;
            }
        }
        timeSlots.add("12:00 PM - 1:00 PM (Lunch Break)");
        hour = 13;
        minute = 0;
        for (int i = 0; i < 2; i++) {
            timeSlots.add(formatTime(hour, minute) + " - " + formatTime(hour, minute + 50));
            minute += 50;
            if (minute >= 60) {
                minute -= 60;
                hour++;
            }
        }
    }

    private String formatTime(int hour, int minute) {
        String period = (hour >= 12) ? "PM" : "AM";
        int displayHour = (hour % 12 == 0) ? 12 : hour % 12;
        return String.format("%d:%02d %s", displayHour, minute, period);
    }

    private void addTimeSlot() {
        String teacher = (String) teacherBox.getSelectedItem();
        String subject = (String) subjectBox.getSelectedItem();
        String day = (String) dayBox.getSelectedItem();
        String time = (String) timeSlotBox.getSelectedItem();

        if (time == null || time.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please select a valid time slot!");
            return;
        }

        if (isTimeSlotTaken(day, time)) {
            JOptionPane.showMessageDialog(frame, "This time slot is already assigned to another teacher on the same day!");
            return;
        }

        model.addRow(new Object[]{day, time, teacher, subject});

        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO timetable (day, time, teacher, subject) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, day);
            pstmt.setString(2, time);
            pstmt.setString(3, teacher);
            pstmt.setString(4, subject);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isTimeSlotTaken(String day, String time) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM timetable WHERE day = ? AND time = ?")) {
            pstmt.setString(1, day);
            pstmt.setString(2, time);
            ResultSet rs = pstmt.executeQuery();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void deleteTimeSlot() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a row to delete.");
            return;
        }

        String day = (String) model.getValueAt(selectedRow, 0);
        String time = (String) model.getValueAt(selectedRow, 1);
        String teacher = (String) model.getValueAt(selectedRow, 2);

        model.removeRow(selectedRow);

        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM timetable WHERE day = ? AND time = ? AND teacher = ?")) {
            pstmt.setString(1, day);
            pstmt.setString(2, time);
            pstmt.setString(3, teacher);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
private void showTimeSlots() {
    String selectedTeacher = (String) teacherBox.getSelectedItem();
    if (selectedTeacher == null) {
        JOptionPane.showMessageDialog(frame, "Please select a teacher.");
        return;
    }

    StringBuilder slots = new StringBuilder("Time Slots for " + selectedTeacher + ":\n");
    boolean found = false;

    try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT day, time, subject FROM timetable WHERE teacher = ?")) {
        pstmt.setString(1, selectedTeacher);
        ResultSet rs = pstmt.executeQuery();

        while (rs.next()) {
            String day = rs.getString("day");
            String time = rs.getString("time");
            String subject = rs.getString("subject");
            slots.append(day).append(" - ").append(time).append(" (").append(subject).append(")\n");
            found = true;
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    if (!found) {
        slots.append("No time slots assigned.");
    }

    JOptionPane.showMessageDialog(frame, slots.toString());
}

    private void updateSubjectList() {
        String selectedTeacher = (String) teacherBox.getSelectedItem();
        subjectBox.removeAllItems();

        if (selectedTeacher != null) {
            for (String subject : teacherSubjects.get(selectedTeacher)) {
                subjectBox.addItem(subject);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TimetableApp::new);
    }
}
