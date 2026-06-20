import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates the ship-in-the-box synthetic golden set (spec section 8): a handful of clean GST
 * invoices rendered to PNG plus matching ground-truth JSON. GSTINs carry a correct checksum and the
 * arithmetic is internally consistent, so a perfect extraction raises ZERO exceptions.
 *
 * Standalone, JDK-only. Run from the repo root:  java eval/tools/GenGolden.java
 * Operators should REPLACE these with >= 30 real, hand-labelled documents.
 */
public class GenGolden {

    static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String OUT_DIR = "eval/golden";

    public static void main(String[] args) throws IOException {
        new File(OUT_DIR).mkdirs();
        List<Sample> samples = buildSamples();
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            String base = String.format(Locale.ROOT, "sample-%02d", i + 1);
            render(s, OUT_DIR + "/" + base + ".png");
            write(OUT_DIR + "/" + base + ".expected.json", toJson(s));
            System.out.println("wrote " + base + " (gstin=" + s.gstin + ", grandTotal=" + money(s.grandTotal) + ")");
        }
        System.out.println("Done. " + samples.size() + " golden samples in " + OUT_DIR);
    }

    // ---- sample model ----
    static class Line {
        String desc, hsn, uom;
        double qty, rate, discount, gstRate;
        double taxable, lineTotal;
        Line(String desc, String hsn, double qty, String uom, double rate, double discount, double gstRate) {
            this.desc = desc; this.hsn = hsn; this.qty = qty; this.uom = uom;
            this.rate = rate; this.discount = discount; this.gstRate = gstRate;
            this.taxable = round2(qty * rate - discount);
            this.lineTotal = round2(taxable * (1 + gstRate / 100.0));
        }
    }

    static class Sample {
        String supplier, gstin, invoiceNo, isoDate, displayDate, docType;
        boolean interState;
        List<Line> lines = new ArrayList<>();
        double subTotal, cgst, sgst, igst, roundOff, grandTotal;

        void finish() {
            double sub = 0;
            for (Line l : lines) sub += l.taxable;
            subTotal = round2(sub);
            double tax = 0;
            for (Line l : lines) tax += l.taxable * l.gstRate / 100.0;
            tax = round2(tax);
            if (interState) {
                igst = tax;
            } else {
                cgst = round2(tax / 2.0);
                sgst = round2(tax - cgst);
            }
            roundOff = 0.0;
            grandTotal = round2(subTotal + cgst + sgst + igst + roundOff);
        }
    }

    static List<Sample> buildSamples() {
        List<Sample> out = new ArrayList<>();

        Sample s1 = new Sample();
        s1.supplier = "Sri Balaji Pharma Distributors";
        s1.gstin = gstin("29", "ABCDE1234F", '1');
        s1.invoiceNo = "SBP/24-25/00187"; s1.isoDate = "2024-05-14"; s1.displayDate = "14/05/2024";
        s1.docType = "INVOICE"; s1.interState = false;
        s1.lines.add(new Line("Paracetamol 500mg", "3004", 100, "BOX", 45.00, 0.00, 12));
        s1.lines.add(new Line("Amoxicillin 250mg", "3004", 50, "BOX", 90.00, 50.00, 12));
        s1.finish(); out.add(s1);

        Sample s2 = new Sample();
        s2.supplier = "Annapurna FMCG Wholesale";
        s2.gstin = gstin("27", "PQRST5678K", '1');
        s2.invoiceNo = "AFW-2024-3321"; s2.isoDate = "2024-08-09"; s2.displayDate = "09/08/2024";
        s2.docType = "INVOICE"; s2.interState = false;
        s2.lines.add(new Line("Basmati Rice Bag 25kg", "1006", 20, "BAG", 1200.00, 0.00, 18));
        s2.lines.add(new Line("Refined Sugar 50kg", "1701", 10, "BAG", 2000.00, 0.00, 18));
        s2.lines.add(new Line("Cooking Oil 15L", "1507", 5, "TIN", 1500.00, 0.00, 18));
        s2.finish(); out.add(s2);

        Sample s3 = new Sample();
        s3.supplier = "MediSupply Wholesale Pvt Ltd";
        s3.gstin = gstin("06", "LMNOP9012Q", '1');
        s3.invoiceNo = "MS-2024-9931"; s3.isoDate = "2024-06-02"; s3.displayDate = "02/06/2024";
        s3.docType = "INVOICE"; s3.interState = true;
        s3.lines.add(new Line("Surgical Gloves", "4015", 10, "PKT", 200.00, 0.00, 18));
        s3.lines.add(new Line("Face Mask N95", "6307", 100, "PCS", 25.00, 0.00, 18));
        s3.finish(); out.add(s3);

        Sample s4 = new Sample();
        s4.supplier = "Gokul Dairy Products";
        s4.gstin = gstin("33", "UVWXY3456R", '1');
        s4.invoiceNo = "GDP/0455"; s4.isoDate = "2024-09-21"; s4.displayDate = "21/09/2024";
        s4.docType = "BILL"; s4.interState = false;
        s4.lines.add(new Line("Milk Powder 1kg", "0402", 40, "PKT", 320.00, 0.00, 5));
        s4.finish(); out.add(s4);

        return out;
    }

    // ---- rendering ----
    static void render(Sample s, String path) throws IOException {
        int w = 1000, h = 1400;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);

        Font title = new Font("SansSerif", Font.BOLD, 28);
        Font normal = new Font("Monospaced", Font.PLAIN, 20);
        Font small = new Font("Monospaced", Font.PLAIN, 18);

        int x = 40, y = 50;
        g.setFont(title); g.drawString(s.supplier, x, y); y += 34;
        g.setFont(normal);
        g.drawString("GSTIN: " + s.gstin, x, y); y += 28;
        g.drawString("Tax Invoice  No: " + s.invoiceNo + "   Date: " + s.displayDate, x, y); y += 28;
        g.drawString("Document Type: " + s.docType, x, y); y += 40;

        g.setFont(small);
        g.drawString(pad("HSN", 6) + pad("Description", 26) + pad("Qty", 6) + pad("UOM", 5)
                + pad("Rate", 10) + pad("Disc", 9) + pad("Taxable", 11) + pad("GST%", 6) + "Amount", x, y);
        y += 24;
        g.drawString(repeat('-', 86), x, y); y += 24;
        for (Line l : s.lines) {
            String row = pad(l.hsn, 6) + pad(trunc(l.desc, 25), 26) + pad(num(l.qty), 6) + pad(l.uom, 5)
                    + pad(money(l.rate), 10) + pad(money(l.discount), 9) + pad(money(l.taxable), 11)
                    + pad(num(l.gstRate) + "%", 6) + money(l.lineTotal);
            g.drawString(row, x, y); y += 26;
        }
        y += 20;
        int tx = 560;
        g.drawString("Sub Total : " + money(s.subTotal), tx, y); y += 26;
        if (s.interState) {
            g.drawString("IGST      : " + money(s.igst), tx, y); y += 26;
        } else {
            g.drawString("CGST      : " + money(s.cgst), tx, y); y += 26;
            g.drawString("SGST      : " + money(s.sgst), tx, y); y += 26;
        }
        g.drawString("Round Off : " + money(s.roundOff), tx, y); y += 26;
        g.setFont(normal);
        g.drawString("GRAND TOTAL : " + money(s.grandTotal) + " INR", tx, y);

        g.dispose();
        ImageIO.write(img, "png", new File(path));
    }

    // ---- JSON (hand-built, fixed shape) ----
    static String toJson(Sample s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"docType\": ").append(qs(s.docType)).append(",\n");
        sb.append("  \"header\": {\n");
        sb.append("    \"supplierName\": ").append(qs(s.supplier)).append(",\n");
        sb.append("    \"supplierGstin\": ").append(qs(s.gstin)).append(",\n");
        sb.append("    \"invoiceNumber\": ").append(qs(s.invoiceNo)).append(",\n");
        sb.append("    \"invoiceDate\": ").append(qs(s.isoDate)).append(",\n");
        sb.append("    \"subTotal\": ").append(money(s.subTotal)).append(",\n");
        sb.append("    \"cgst\": ").append(s.interState ? "null" : money(s.cgst)).append(",\n");
        sb.append("    \"sgst\": ").append(s.interState ? "null" : money(s.sgst)).append(",\n");
        sb.append("    \"igst\": ").append(s.interState ? money(s.igst) : "null").append(",\n");
        sb.append("    \"roundOff\": ").append(money(s.roundOff)).append(",\n");
        sb.append("    \"grandTotal\": ").append(money(s.grandTotal)).append(",\n");
        sb.append("    \"currency\": \"INR\"\n");
        sb.append("  },\n");
        sb.append("  \"lineItems\": [\n");
        for (int i = 0; i < s.lines.size(); i++) {
            Line l = s.lines.get(i);
            sb.append("    {\n");
            sb.append("      \"description\": ").append(qs(l.desc)).append(",\n");
            sb.append("      \"hsn\": ").append(qs(l.hsn)).append(",\n");
            sb.append("      \"qty\": ").append(money(l.qty)).append(",\n");
            sb.append("      \"uom\": ").append(qs(l.uom)).append(",\n");
            sb.append("      \"rate\": ").append(money(l.rate)).append(",\n");
            sb.append("      \"discount\": ").append(money(l.discount)).append(",\n");
            sb.append("      \"taxableValue\": ").append(money(l.taxable)).append(",\n");
            sb.append("      \"gstRate\": ").append(money(l.gstRate)).append(",\n");
            sb.append("      \"lineTotal\": ").append(money(l.lineTotal)).append("\n");
            sb.append("    }").append(i < s.lines.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"expectedExceptions\": []\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ---- helpers ----
    static String gstin(String state, String pan, char entity) {
        String first14 = state + pan + entity + "Z";
        return first14 + checkDigit(first14);
    }

    static char checkDigit(String first14) {
        int factor = 2, sum = 0, base = CHARSET.length();
        for (int i = first14.length() - 1; i >= 0; i--) {
            int code = CHARSET.indexOf(first14.charAt(i));
            int p = code * factor;
            factor = (factor == 2) ? 1 : 2;
            p = (p / base) + (p % base);
            sum += p;
        }
        return CHARSET.charAt((base - (sum % base)) % base);
    }

    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    static String money(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    static String num(double v) { return (v == Math.floor(v)) ? String.valueOf((long) v) : money(v); }
    static String pad(String s, int n) { return s.length() >= n ? s + " " : s + " ".repeat(n - s.length()); }
    static String trunc(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }
    static String repeat(char c, int n) { return String.valueOf(c).repeat(n); }
    static String qs(String s) { return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    static void write(String path, String content) throws IOException {
        try (FileWriter fw = new FileWriter(path)) { fw.write(content); }
    }
}
