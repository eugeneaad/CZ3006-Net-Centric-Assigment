
/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/
import java.util.Timer;
import java.util.TimerTask;


public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
   public static final int MAX_SEQ = 7;
   public static final int NR_BUFS = (MAX_SEQ + 1)/2;

   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();
   private Packet out_buf[] = new Packet[NR_BUFS];

   //the following are used for simulation purpose only
   private SWE swe = null;
   private String sid = null;

   //Constructor
   public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
   }

   //the following methods are all protocol related
   private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	       out_buf[i] = new Packet();
      }
   }

   private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
   }

   private void enable_network_layer(int nr_of_bufs) {
   //network layer is permitted to send if credit is available
	    swe.grant_credit(nr_of_bufs);
   }

   private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
   }

   private void to_network_layer(Packet packet) {
	    swe.to_network_layer(packet);
   }

   private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq +
			    " ack = " + fm.ack + " kind = " +
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
   }

   private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer();
	    fm.kind = fm1.kind;
	    fm.seq = fm1.seq;
	    fm.ack = fm1.ack;
	    fm.info = fm1.info;
   }


/*===========================================================================*
 	implement your Protocol Variables and Methods below:
 *==========================================================================*/
  boolean no_nak = true;
  Packet in_buf[] = new Packet[NR_BUFS];

  private boolean between(int a, int b, int c)
  {
    /* Same as between in protocol5, but shorter and more obscure. */
    return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a));
  }

  private void send_frame(int frame_type, int frame_nr, int frame_expected, Packet buffer[])
  {
    /* Construct and send a data, ack, or nak frame. */
    PFrame s = new PFrame();  /* scratch variable */

    s.kind = frame_type;      /* kind == data, ack, or nak */

    if (frame_type == PFrame.DATA)
        s.info = buffer[frame_nr % NR_BUFS];


    s.seq = frame_nr;         /* only meaningful for data frames */
    s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);

    if (frame_type == PFrame.NAK)       /* one nak per frame, please */
        no_nak = false;

    to_physical_layer(s);               /* transmit the frame */

    if (frame_type == PFrame.DATA)
        start_timer(frame_nr);

    stop_ack_timer();                   /* no need for separate ack frame */
  }

  public int inc(int num)
  {
    num = ((num + 1) % (MAX_SEQ + 1));
    return num;
  }

  public void protocol6()
  {
    init();
    boolean arrived[] = new boolean[NR_BUFS];   /* inbound bit map */
    PFrame r = new PFrame();                    /* scratch variable */
    int ack_expected = 0;                       /* lower edge of sender’s window */
    int next_frame_to_send =0;                  /* upper edge of sender’s window + 1 */
    int frame_expected =0;                      /* lower edge of receiver’s window */
    int too_far = NR_BUFS;                      /* upper edge of receiver’s window + 1 */

    enable_network_layer(NR_BUFS);              /* initialize */

    for (int i = 0; i < NR_BUFS; i++)   /*i = index into buffer pool */
    arrived[i] = false;

     while(true) {
        wait_for_event(event);                /* five possibilities: see event3type above */
        switch(event.type) {
            case (PEvent.NETWORK_LAYER_READY):      /* accept, save, and transmit a new frame */
                from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);      /* fetch new packet */
                send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);   /* transmit the frame */
                next_frame_to_send = inc(next_frame_to_send);         /* advance upper window edge */
                break;

            case (PEvent.FRAME_ARRIVAL):          /* a data or control frame has arrived */
                from_physical_layer(r);           /* fetch incoming frame from physical layer */

                if (r.kind == PFrame.DATA) {
                    if ((r.seq != frame_expected) && no_nak)
                        send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                    else
                        start_ack_timer();
                    if (between(frame_expected, r.seq, too_far) && arrived[r.seq % NR_BUFS] == false) {
                      /* Frames may be accepted in any order. */
                        arrived[r.seq % NR_BUFS] = true;      /* mark buffer as full */
                        in_buf[r.seq % NR_BUFS] = r.info;     /* insert data into buffer */

                        while (arrived[frame_expected % NR_BUFS]) {
                            // Pass frames from the physical layer to the network layer and advance window.

                            to_network_layer(in_buf[frame_expected % NR_BUFS]);
                            no_nak = true;
                            arrived[frame_expected % NR_BUFS] = false;
                            frame_expected = inc(frame_expected);       /* advance lower edge of receiver’s window */
                            too_far = inc(too_far);                     /* advance upper edge of receiver¡¯s window */
                            start_ack_timer();                          /* to see if a separate ack is needed */
                        }
                    }
                }

                if (r.kind == PFrame.NAK && between(ack_expected, ((r.ack + 1) % (MAX_SEQ + 1)),
                next_frame_to_send)) {
                    send_frame(PFrame.DATA, ((r.ack + 1) % (MAX_SEQ + 1)), frame_expected, out_buf);
                }

                while (between(ack_expected, r.ack, next_frame_to_send)) {
                    stop_timer(ack_expected % NR_BUFS);             /* frame arrived intact */
                    ack_expected = inc(ack_expected);               /* advance lower edge of sender’s window */
                    enable_network_layer(1);                        // free one buffer slot.
                }

                break;

            case (PEvent.CKSUM_ERR):
                /* damaged frame */
                if (no_nak)
                  send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                break;
            case (PEvent.TIMEOUT):
                 // If the timer has expired, resend the frame.
                send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
                break;
            case (PEvent.ACK_TIMEOUT):
                /* Ack timer expired; send ack */
                send_frame(PFrame.ACK, 0, frame_expected, out_buf);
                break;

            default:
                System.out.println("SWP: undefined event type = " + event.type);
                System.out.flush();
                break;
            }
    }
  }

 /* Note: when start_timer() and stop_timer() are called,
    the "seq" parameter must be the sequence number, rather
    than the index of the timer array,
    of the frame associated with this timer,
   */
   Timer timer[] = new Timer[NR_BUFS];

   private void start_timer(int seq) {
     stop_timer(seq);
     timer[seq % NR_BUFS] = new Timer();                 //Map the sequnece number and init
       TimerTask timerTask = new TimerTask() {           //Task to perform when time out
             @Override
             public void run() {
                 stop_timer(seq);                       //Terminate timer
                 swe.generate_timeout_event(seq);       //Generate timeout event with the sequence number
             }
         };
         timer[seq % NR_BUFS].schedule(timerTask,200);  //Start the timer with schedule delay

   }

   private void stop_timer(int seq) {
     if (timer[seq % NR_BUFS] != null) {
            timer[seq % NR_BUFS].cancel();              //Map the sequnece number and stop the corresponding timer
        }
   }

   Timer ackTimer;

   private void start_ack_timer( ) {
     //stop_ack_timer();
     ackTimer = new Timer();                            //Init the timer
     ackTimer.schedule(new AckTimerTask(swe), 100);     //Create a timertask for the timer
   }

   private void stop_ack_timer() {
     if (ackTimer != null) {
            ackTimer.cancel();                //Terminate timer
        }
   }

   class AckTimerTask extends TimerTask {
       private SWE swe = null;

       public AckTimerTask(SWE _swe) {
           swe = _swe;
       }
       public void run() {
           swe.generate_acktimeout_event();   //Generate ACK time out event
           stop_ack_timer();                  //Terminate timer
       }
   }
}//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/
