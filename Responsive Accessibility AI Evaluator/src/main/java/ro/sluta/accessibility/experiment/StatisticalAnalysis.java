package ro.sluta.accessibility.experiment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class StatisticalAnalysis {
    private StatisticalAnalysis() { }
    public record Interval(double lower, double upper, double confidence) { }
    public record BootstrapResult(double estimate, Interval interval, int resamples, long seed) { }
    public record WilcoxonResult(double wPlus, double wMinus, double z, double pValue, int pairs) { }

    public static Interval wilson(int successes, int total) {
        if (total <= 0) return new Interval(0, 0, 0.95);
        double z = 1.959963984540054, p = successes / (double) total, denominator = 1 + z * z / total;
        double center = (p + z * z / (2 * total)) / denominator;
        double margin = z * Math.sqrt((p * (1 - p) + z * z / (4 * total)) / total) / denominator;
        return new Interval(Math.max(0, center - margin), Math.min(1, center + margin), 0.95);
    }

    public static BootstrapResult pairedBootstrap(double[] left, double[] right, int resamples, long seed) {
        if (left.length == 0 || left.length != right.length || resamples < 100)
            throw new IllegalArgumentException("Bootstrapul cere perechi și minimum 100 reeșantionări.");
        double[] values = new double[resamples]; Random random = new Random(seed);
        for (int sample = 0; sample < resamples; sample++) {
            double sum = 0; for (int index = 0; index < left.length; index++) { int pick = random.nextInt(left.length); sum += left[pick] - right[pick]; }
            values[sample] = sum / left.length;
        }
        Arrays.sort(values); return new BootstrapResult(meanDifference(left, right),
                new Interval(percentile(values, .025), percentile(values, .975), .95), resamples, seed);
    }

    public static double mcnemarExactPValue(int leftOnly, int rightOnly) {
        int n = leftOnly + rightOnly; if (n == 0) return 1;
        int k = Math.min(leftOnly, rightOnly); double cumulative = 0;
        for (int index = 0; index <= k; index++) cumulative += binomialProbability(n, index);
        return Math.min(1, 2 * cumulative);
    }

    public static WilcoxonResult wilcoxon(double[] left, double[] right) {
        if (left.length != right.length) throw new IllegalArgumentException("Wilcoxon cere perechi.");
        List<Ranked> differences = new ArrayList<>();
        for (int index = 0; index < left.length; index++) { double d=left[index]-right[index]; if (d != 0) differences.add(new Ranked(Math.abs(d), Math.signum(d), 0)); }
        differences.sort(Comparator.comparingDouble(Ranked::absolute));
        double wPlus=0,wMinus=0; int cursor=0;
        while (cursor < differences.size()) { int end=cursor+1; while(end<differences.size() && differences.get(end).absolute==differences.get(cursor).absolute) end++;
            double rank=((cursor+1)+end)/2.0; for(int index=cursor;index<end;index++) if(differences.get(index).sign>0)wPlus+=rank;else wMinus+=rank; cursor=end; }
        int n=differences.size(); if(n==0)return new WilcoxonResult(0,0,0,1,0);
        double mean=n*(n+1)/4.0, variance=n*(n+1)*(2*n+1)/24.0, z=(wPlus-mean)/Math.sqrt(variance);
        return new WilcoxonResult(wPlus,wMinus,z,Math.min(1,2*(1-normalCdf(Math.abs(z)))),n);
    }

    public static Map<String, Double> holm(Map<String, Double> pValues) {
        var entries = pValues.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
        Map<String,Double> adjusted=new LinkedHashMap<>(); double previous=0; int m=entries.size();
        for(int index=0;index<m;index++){double value=Math.max(previous,Math.min(1,(m-index)*entries.get(index).getValue()));adjusted.put(entries.get(index).getKey(),value);previous=value;}
        return Map.copyOf(adjusted);
    }

    public static double cohenKappa(List<?> left, List<?> right) {
        if(left.isEmpty()||left.size()!=right.size())throw new IllegalArgumentException("Kappa cere perechi.");
        var labels=new java.util.HashSet<Object>();labels.addAll(left);labels.addAll(right);double observed=0,expected=0;
        for(int i=0;i<left.size();i++)if(java.util.Objects.equals(left.get(i),right.get(i)))observed++;
        observed/=left.size();for(Object label:labels){long a=left.stream().filter(label::equals).count(),b=right.stream().filter(label::equals).count();expected+=(a/(double)left.size())*(b/(double)right.size());}
        return expected==1?1:(observed-expected)/(1-expected);
    }

    public static double weightedKappa(List<Integer> left, List<Integer> right, int maximumScore) {
        if(left.isEmpty()||left.size()!=right.size()||maximumScore<=0)throw new IllegalArgumentException("Weighted kappa cere scoruri pereche.");
        int n=left.size();double observed=0,expected=0;int[] lc=new int[maximumScore+1],rc=new int[maximumScore+1];
        for(int i=0;i<n;i++){int a=left.get(i),b=right.get(i);if(a<0||a>maximumScore||b<0||b>maximumScore)throw new IllegalArgumentException("Scor invalid.");lc[a]++;rc[b]++;observed+=weight(a,b,maximumScore);}
        observed/=n;for(int a=0;a<=maximumScore;a++)for(int b=0;b<=maximumScore;b++)expected+=weight(a,b,maximumScore)*(lc[a]/(double)n)*(rc[b]/(double)n);
        return expected==1?1:(observed-expected)/(1-expected);
    }

    private static double weight(int a,int b,int max){double d=(a-b)/(double)max;return 1-d*d;}
    private static double meanDifference(double[] a,double[] b){double sum=0;for(int i=0;i<a.length;i++)sum+=a[i]-b[i];return sum/a.length;}
    private static double percentile(double[] values,double p){double position=p*(values.length-1);int low=(int)Math.floor(position),high=(int)Math.ceil(position);return values[low]+(values[high]-values[low])*(position-low);}
    private static double binomialProbability(int n,int k){double coefficient=1;for(int i=1;i<=k;i++)coefficient*=((n-k+i)/(double)i);return coefficient*Math.pow(.5,n);}
    private static double normalCdf(double x){double t=1/(1+0.2316419*x),density=0.3989422804014327*Math.exp(-x*x/2);return 1-density*t*(0.319381530+t*(-0.356563782+t*(1.781477937+t*(-1.821255978+t*1.330274429))));}
    private record Ranked(double absolute,double sign,double rank){ }
}
