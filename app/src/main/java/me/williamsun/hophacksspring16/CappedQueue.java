package me.williamsun.hophacksspring16;

/**
 * Custom data structure to track the last 50 points of data. Only needed to insert and find average.
 * Created by William on 2/7/16.
 */
public class CappedQueue {
    double[] contents;
    public int added;
    double total;
    public CappedQueue(){
        contents = new double[50];
        added = 0;
        total = 0;
    }

    public double[] orderedAbs(){
        double[] ret;
        if(added < 50){
            ret = new double[added];
            int size = contents.length;
            for(int i = 0; i < size; i++){
                ret[i] = Math.abs(contents[i]);
            }
        } else {
            ret = new double[50];
            int startIndex = added % 50;
            int diff = 50 - startIndex;
            for(int x = startIndex; x < 50; x++){
                ret[x - startIndex] = Math.abs(contents[x]);
            }
            for(int x = 0; x < startIndex; x++){
                ret[diff + x] = Math.abs(contents[x]);
            }
        }

        return ret;
    }

    public void insert(double d){
        if(added < 50){
            contents[added] = d;
            total += d;
        } else {
            int index = added % 50;
            total -= contents[index];
            contents[index] = d;
            total += d;
        }
        added++;
    }

    public double average(){
        if(added < 50){
            return total / added;
        } else {
            return total / 50;
        }
    }

    public double deviation(){
        double avg = average();
        int limit = contents.length;
        double dev = 0;
        for(int i = 0; i < limit; i++){
            dev += Math.pow(contents[i] - avg, 2);
        }
        dev = Math.sqrt(dev / 50.0);
        return dev;
    }
}
