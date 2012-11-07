package com.irccloud.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;

import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gcm.GCMRegistrar;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MessageActivity extends BaseActivity  implements UsersListFragment.OnUserSelectedListener, BuffersListFragment.OnBufferSelectedListener, MessageViewFragment.MessageViewListener {
	int cid = -1;
	int bid = -1;
	String name;
	String type;
	TextView messageTxt;
	View sendBtn;
	int joined;
	int archived;
	String status;
	UsersDataSource.User selected_user;
	View userListView;
	View buffersListView;
	TextView title;
	TextView subtitle;
	LinearLayout messageContainer;
	HorizontalScrollView scrollView;
	NetworkConnection conn;
	private boolean shouldFadeIn = false;
	ImageView upView;
	private RefreshUpIndicatorTask refreshUpIndicatorTask = null;
	private ArrayList<Integer> backStack = new ArrayList<Integer>();
	PowerManager.WakeLock screenLock = null;
	private int launchBid = -1;
	
	private HashMap<Integer, EventsDataSource.Event> pendingEvents = new HashMap<Integer, EventsDataSource.Event>();
	
    @SuppressLint("NewApi")
	@SuppressWarnings({ "deprecation", "unchecked" })
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        buffersListView = findViewById(R.id.BuffersList);
        messageContainer = (LinearLayout)findViewById(R.id.messageContainer);
        scrollView = (HorizontalScrollView)findViewById(R.id.scroll);
        
        if(scrollView != null) {
	        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)messageContainer.getLayoutParams();
	        params.width = getWindowManager().getDefaultDisplay().getWidth();
	        messageContainer.setLayoutParams(params);
        }
        messageTxt = (TextView)findViewById(R.id.messageTxt);
        messageTxt.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent event) {
         	   if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
         		   new SendTask().execute((Void)null);
         	   }
         	   return true;
           	}
        });
        messageTxt.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            	Object[] spans = s.getSpans(0, s.length(), Object.class);
            	for(Object o : spans) {
            		if(((s.getSpanFlags(o) & Spanned.SPAN_COMPOSING) != Spanned.SPAN_COMPOSING) && (o.getClass() == StyleSpan.class || o.getClass() == ForegroundColorSpan.class || o.getClass() == BackgroundColorSpan.class || o.getClass() == UnderlineSpan.class)) {
            			s.removeSpan(o);
            		}
            	}
            	if(s.length() > 0) {
	           		sendBtn.setEnabled(true);
	           		if(Build.VERSION.SDK_INT >= 11)
	           			sendBtn.setAlpha(1);
            	} else {
	           		sendBtn.setEnabled(false);
	           		if(Build.VERSION.SDK_INT >= 11)
	           			sendBtn.setAlpha(0.5f);
            	}
            }

            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
            }
        });
        sendBtn = findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new SendTask().execute((Void)null);
			}
        });
        userListView = findViewById(R.id.usersListFragment);
        
        getSupportActionBar().setLogo(R.drawable.logo);
        getSupportActionBar().setHomeButtonEnabled(false);
       	getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        
        View v = getLayoutInflater().inflate(R.layout.actionbar_messageview, null);
        v.findViewById(R.id.actionTitleArea).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
            	ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(bid);
            	if(c != null) {
            		AlertDialog.Builder builder = new AlertDialog.Builder(MessageActivity.this);
	            	builder.setTitle("Channel Topic");
	            	if(c.topic_text.length() > 0) {
	            		final SpannableString s = new SpannableString(c.topic_text);
	            		Linkify.addLinks(s, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
	            		builder.setMessage(s);
	            	} else
	            		builder.setMessage("No topic set.");
	            	builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
	            	});
	            	builder.setNeutralButton("Edit", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							editTopic();
						}
	            	});
		    		AlertDialog dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.show();
		    		((TextView)dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            	} else if(archived == 0 && subtitle.getText().length() > 0){
            		AlertDialog.Builder builder = new AlertDialog.Builder(MessageActivity.this);
	            	builder.setTitle(title.getText().toString());
            		final SpannableString s = new SpannableString(subtitle.getText().toString());
            		Linkify.addLinks(s, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            		builder.setMessage(s);
	            	builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
	            	});
		    		AlertDialog dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.show();
		    		((TextView)dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            	}
			}
        });

        upView = (ImageView)v.findViewById(R.id.upIndicator);
        if(scrollView != null) {
        	upView.setVisibility(View.VISIBLE);
        	upView.setOnClickListener(upClickListener);
	        ImageView icon = (ImageView)v.findViewById(R.id.upIcon);
	        icon.setOnClickListener(upClickListener);
	        if(refreshUpIndicatorTask != null)
	        	refreshUpIndicatorTask.cancel(true);
	        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
	        refreshUpIndicatorTask.execute((Void)null);
        } else {
        	upView.setVisibility(View.INVISIBLE);
        }

        title = (TextView)v.findViewById(R.id.title);
        subtitle = (TextView)v.findViewById(R.id.subtitle);
        getSupportActionBar().setCustomView(v);
        
        if(savedInstanceState != null && savedInstanceState.containsKey("cid")) {
        	cid = savedInstanceState.getInt("cid");
        	bid = savedInstanceState.getInt("bid");
        	name = savedInstanceState.getString("name");
        	type = savedInstanceState.getString("type");
        	joined = savedInstanceState.getInt("joined");
        	archived = savedInstanceState.getInt("archived");
        	status = savedInstanceState.getString("status");
        	backStack = (ArrayList<Integer>) savedInstanceState.getSerializable("backStack");
        }
        if(getSharedPreferences("prefs", 0).contains("session_key")) {
	        GCMRegistrar.checkDevice(this);
	        GCMRegistrar.checkManifest(this);
	        final String regId = GCMRegistrar.getRegistrationId(this);
	        if (regId.equals("")) {
	        	GCMRegistrar.register(this, GCMIntentService.GCM_ID);
	        } else {
	        	if(getSharedPreferences("prefs", 0).getInt("GCM_VERSION", 0) != GCMIntentService.versionCode())
	        		GCMRegistrar.unregister(this);
	        	else if(!getSharedPreferences("prefs", 0).contains("gcm_registered"))
	        		GCMIntentService.scheduleRegisterTimer(30000);
	        }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putInt("cid", cid);
    	state.putInt("bid", bid);
    	state.putString("name", name);
    	state.putString("type", type);
    	state.putInt("joined", joined);
    	state.putInt("archived", archived);
    	state.putString("status", status);
    	state.putSerializable("backStack", backStack);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) { //Back key pressed
        	if(backStack != null && backStack.size() > 0) {
        		Integer bid = backStack.get(0);
        		backStack.remove(0);
        		BuffersDataSource.Buffer buffer = BuffersDataSource.getInstance().getBuffer(bid);
        		if(buffer != null) {
	    			onBufferSelected(buffer.cid, buffer.bid, buffer.name, buffer.last_seen_eid, buffer.min_eid, 
	    					buffer.type, 1, buffer.archived, status);
	    			if(backStack.size() > 0)
	    				backStack.remove(0);
        		} else {
        	        return super.onKeyDown(keyCode, event);
        		}
                return true;
        	}
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private class SendTask extends AsyncTaskEx<Void, Void, Void> {
    	EventsDataSource.Event e = null;
    	
    	@Override
    	protected void onPreExecute() {
			if(conn.getState() == NetworkConnection.STATE_CONNECTED) {
	    		sendBtn.setEnabled(false);
	    		ServersDataSource.Server s = ServersDataSource.getInstance().getServer(cid);
	    		UsersDataSource.User u = UsersDataSource.getInstance().getUser(cid, name, s.nick);
	    		e = EventsDataSource.getInstance().new Event();
	    		e.cid = cid;
	    		e.bid = bid;
	    		e.eid = (System.currentTimeMillis() + conn.clockOffset) * 1000L;
	    		e.self = true;
	    		e.from = s.nick;
	    		e.nick = s.nick;
	    		if(u != null)
	    			e.from_mode = u.mode;
	    		String msg = messageTxt.getText().toString();
	    		if(msg.startsWith("//"))
	    			msg = msg.substring(1);
	    		else if(msg.startsWith("/") && !msg.startsWith("/me "))
	    			msg = null;
	    		e.msg = msg;
	    		if(msg != null && msg.toLowerCase().startsWith("/me ")) {
		    		e.type = "buffer_me_msg";
		    		e.msg = msg.substring(4);
	    		} else {
		    		e.type = "buffer_msg";
	    		}
				e.color = R.color.timestamp;
				if(name.equals(s.nick))
					e.bg_color = R.color.message_bg;
				else
					e.bg_color = R.color.self;
		    	e.row_type = 0;
		    	e.html = null;
		    	e.group_msg = null;
		    	e.linkify = true;
		    	e.target_mode = null;
		    	e.highlight = false;
		    	e.reqid = -1;
		    	if(e.msg != null) {
		    		EventsDataSource.getInstance().addEvent(e);
		    		conn.notifyHandlers(NetworkConnection.EVENT_BUFFERMSG, e, mHandler);
		    	}
			}
    	}
    	
		@Override
		protected Void doInBackground(Void... arg0) {
			if(conn.getState() == NetworkConnection.STATE_CONNECTED) {
				e.reqid = conn.say(cid, name, messageTxt.getText().toString());
				if(e.msg != null)
					pendingEvents.put(e.reqid, e);
			}
			return null;
		}
    	
		@Override
		protected void onPostExecute(Void result) {
			messageTxt.setText("");
    		sendBtn.setEnabled(true);
		}
    }
    
    private class RefreshUpIndicatorTask extends AsyncTaskEx<Void, Void, Void> {
		int unread = 0;
		int highlights = 0;
    	
		@Override
		protected Void doInBackground(Void... arg0) {
			ArrayList<ServersDataSource.Server> servers = ServersDataSource.getInstance().getServers();

			JSONObject disabledMap = null;
			if(conn != null && conn.getUserInfo() != null && conn.getUserInfo().prefs != null && conn.getUserInfo().prefs.has("channel-disableTrackUnread")) {
				try {
					disabledMap = conn.getUserInfo().prefs.getJSONObject("channel-disableTrackUnread");
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			for(int i = 0; i < servers.size(); i++) {
				ServersDataSource.Server s = servers.get(i);
				ArrayList<BuffersDataSource.Buffer> buffers = BuffersDataSource.getInstance().getBuffersForServer(s.cid);
				for(int j = 0; j < buffers.size(); j++) {
					BuffersDataSource.Buffer b = buffers.get(j);
					if(b.type == null)
						Log.w("IRCCloud", "Buffer with null type: " + b.bid + " name: " + b.name);
					if(b.bid != bid) {
						if(unread == 0) {
							int u = 0;
							try {
								u = EventsDataSource.getInstance().getUnreadCountForBuffer(b.bid, b.last_seen_eid, b.type);
								if(disabledMap != null && disabledMap.has(String.valueOf(b.bid)) && disabledMap.getBoolean(String.valueOf(b.bid)))
									u = 0;
							} catch (JSONException e) {
								e.printStackTrace();
							}
							unread += u;
						}
						if(highlights == 0)
							highlights += EventsDataSource.getInstance().getHighlightCountForBuffer(b.bid, b.last_seen_eid, b.type);
					}
				}
			}
			return null;
		}
    	
		@Override
		protected void onPostExecute(Void result) {
			if(!isCancelled()) {
				if(highlights > 0) {
					upView.setImageResource(R.drawable.up_highlight);
				} else if(unread > 0) {
					upView.setImageResource(R.drawable.up_unread);
				} else {
					upView.setImageResource(R.drawable.up);
				}
				refreshUpIndicatorTask = null;
			}
		}
    }
    
    private void setFromIntent(Intent intent) {
    	long min_eid = 0;
    	long last_seen_eid = 0;

    	bid = intent.getIntExtra("bid", 0);

    	if(intent.hasExtra("cid")) {
	    	cid = intent.getIntExtra("cid", 0);
	    	name = intent.getStringExtra("name");
	    	type = intent.getStringExtra("type");
	    	joined = intent.getIntExtra("joined", 0);
	    	archived = intent.getIntExtra("archived", 0);
	    	status = intent.getStringExtra("status");
	    	min_eid = intent.getLongExtra("min_eid", 0);
	    	last_seen_eid = intent.getLongExtra("last_seen_eid", 0);
	    	
	    	if(bid == -1) {
	    		BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBufferByName(cid, name);
	    		if(b != null) {
	    			bid = b.bid;
	    			last_seen_eid = b.last_seen_eid;
	    			min_eid = b.min_eid;
	    			archived = b.archived;
	    		}
	    	}
    	} else if(bid != -1) {
    		BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBuffer(bid);
    		if(b != null) {
				ServersDataSource.Server s = ServersDataSource.getInstance().getServer(b.cid);
				joined = 1;
				if(b.type.equalsIgnoreCase("channel")) {
					ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(b.bid);
					if(c == null)
						joined = 0;
				}
				if(b.type.equalsIgnoreCase("console"))
					b.name = s.name;
	    		cid = b.cid;
	    		name = b.name;
	    		type = b.type;
	    		archived = b.archived;
	    		min_eid = b.min_eid;
	    		last_seen_eid = b.last_seen_eid;
	    		status = s.status;
    		}
    	}

    	if(cid == -1) {
			launchBid = bid;
    	} else {
	    	UsersListFragment ulf = (UsersListFragment)getSupportFragmentManager().findFragmentById(R.id.usersListFragment);
	    	MessageViewFragment mvf = (MessageViewFragment)getSupportFragmentManager().findFragmentById(R.id.messageViewFragment);
	    	Bundle b = new Bundle();
	    	b.putInt("cid", cid);
	    	b.putInt("bid", bid);
	    	b.putLong("last_seen_eid", last_seen_eid);
	    	b.putLong("min_eid", min_eid);
	    	b.putString("name", name);
	    	b.putString("type", type);
	    	ulf.setArguments(b);
	    	mvf.setArguments(b);
    	}
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
    	if(intent != null && intent.hasExtra("bid")) {
    		setFromIntent(intent);
    	}
    }
    
    @SuppressLint("NewApi")
	@Override
    public void onResume() {
    	super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(IRCCloudApplication.getInstance().getApplicationContext());
    	if(prefs.getBoolean("screenlock", false)) {
    		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    	} else {
    		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    	}
    	
    	conn = NetworkConnection.getInstance();
    	conn.addHandler(mHandler);

    	if(conn.getState() != NetworkConnection.STATE_CONNECTED) {
    		if(scrollView != null && ServersDataSource.getInstance().count() == 0)
    			scrollView.setEnabled(false);
    		messageTxt.setEnabled(false);
    	} else {
    		if(scrollView != null)
    			scrollView.setEnabled(true);
    		messageTxt.setEnabled(true);
    	}

    	if(cid == -1) {
	    	if(getIntent() != null && getIntent().hasExtra("bid")) {
	    		setFromIntent(getIntent());
	    	} else if(conn.getState() == NetworkConnection.STATE_CONNECTED && conn.getUserInfo() != null) {
	    		if(!open_bid(conn.getUserInfo().last_selected_bid))
	    			if(!open_bid(BuffersDataSource.getInstance().firstBid())) {
	    				//if(scrollView != null)
	    					//scrollView.scrollTo(0,0);
	    			}
	    	}
    	}
    	updateUsersListFragmentVisibility();
    	title.setText(name);
    	getSupportActionBar().setTitle(name);
    	update_subtitle();
    	if(getSupportFragmentManager().findFragmentById(R.id.BuffersList) != null)
    		((BuffersListFragment)getSupportFragmentManager().findFragmentById(R.id.BuffersList)).setSelectedBid(bid);
    	
        if(refreshUpIndicatorTask != null)
        	refreshUpIndicatorTask.cancel(true);
        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
        refreshUpIndicatorTask.execute((Void)null);

    	invalidateOptionsMenu();
    	
    	Notifications.getInstance().excludeBid(bid);
    	Notifications.getInstance().showNotifications(null);
        IntentFilter filter = new IntentFilter("com.irccloud.android.LAUNCH_BID");
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addCategory(getPackageName());
        registerReceiver(bidLaunchReciever, filter);
   		sendBtn.setEnabled(messageTxt.getText().length() > 0);
   		if(Build.VERSION.SDK_INT >= 11 && messageTxt.getText().length() == 0)
   			sendBtn.setAlpha(0.5f);
    }

    @Override
    public void onPause() {
    	super.onPause();
    	if(conn != null)
    		conn.removeHandler(mHandler);
    	Notifications.getInstance().excludeBid(-1);
    	unregisterReceiver(bidLaunchReciever);
    }
	
    private boolean open_bid(int bid) {
		BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBuffer(bid);
		if(b != null) {
			ServersDataSource.Server s = ServersDataSource.getInstance().getServer(b.cid);
			int joined = 1;
			if(b.type.equalsIgnoreCase("channel")) {
				ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(b.bid);
				if(c == null)
					joined = 0;
			}
			if(b.type.equalsIgnoreCase("console"))
				b.name = s.name;
			onBufferSelected(b.cid, b.bid, b.name, b.last_seen_eid, b.min_eid, b.type, joined, b.archived, s.status);
			return true;
		}
		return false;
    }
    
    private BroadcastReceiver bidLaunchReciever = new BroadcastReceiver() {

		@Override
		public void onReceive(Context ctx, Intent i) {
			if(isOrderedBroadcast()) {
				open_bid(i.getIntExtra("bid", -1));
				abortBroadcast();
			}
		}
    	
    };
    
    private void update_subtitle() {
    	if(cid == -1) {
    		
           	getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowCustomEnabled(false);
    	} else {
           	getSupportActionBar().setDisplayShowHomeEnabled(false);
            getSupportActionBar().setDisplayShowCustomEnabled(true);

            if(archived > 0 && !type.equalsIgnoreCase("console")) {
	    		subtitle.setVisibility(View.VISIBLE);
	    		subtitle.setText("(archived)");
	    	} else {
	    		if(type == null) {
	        		subtitle.setVisibility(View.GONE);
	    		} else if(type.equalsIgnoreCase("conversation")) {
	        		UsersDataSource.User user = UsersDataSource.getInstance().getUser(cid, name);
	    			BuffersDataSource.Buffer b = BuffersDataSource.getInstance().getBuffer(bid);
	    			if(user != null && user.away > 0) {
		        		subtitle.setVisibility(View.VISIBLE);
	    				if(user.away_msg != null && user.away_msg.length() > 0) {
	    					subtitle.setText("Away: " + user.away_msg);
	    				} else if(b != null && b.away_msg != null && b.away_msg.length() > 0) {
	    	        		subtitle.setText("Away: " + b.away_msg);
	    				} else {
	    					subtitle.setText("Away");
	    				}
	    			} else {
		        		subtitle.setVisibility(View.GONE);
	    			}
	    		} else if(type.equalsIgnoreCase("channel")) {
		        	ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(bid);
		        	if(c != null && c.topic_text.length() > 0) {
		        		subtitle.setVisibility(View.VISIBLE);
		        		subtitle.setText(c.topic_text);
		        	} else {
		        		subtitle.setVisibility(View.GONE);
		        	}
	    		} else if(type.equalsIgnoreCase("console")) {
	    			ServersDataSource.Server s = ServersDataSource.getInstance().getServer(cid);
	    			if(s != null) {
		        		subtitle.setVisibility(View.VISIBLE);
		        		subtitle.setText(s.hostname + ":" + s.port);
		        	} else {
		        		subtitle.setVisibility(View.GONE);
	    			}
	    		}
	    	}
    	}
    }
    
    private void updateUsersListFragmentVisibility() {
    	boolean hide = false;
		if(userListView != null) {
			try {
				if(conn != null && conn.getUserInfo() != null && conn.getUserInfo().prefs != null) {
					JSONObject hiddenMap = conn.getUserInfo().prefs.getJSONObject("channel-hiddenMembers");
					if(hiddenMap.has(String.valueOf(bid)) && hiddenMap.getBoolean(String.valueOf(bid)))
						hide = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
	    	if(hide || type == null || !type.equalsIgnoreCase("channel"))
	    		userListView.setVisibility(View.GONE);
	    	else
	    		userListView.setVisibility(View.VISIBLE);
		}
    }
    
	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			Integer event_bid = 0;
			IRCCloudJSONObject event = null;
			switch (msg.what) {
			case NetworkConnection.EVENT_CONNECTIVITY:
				if(conn.getState() == NetworkConnection.STATE_CONNECTED) {
					for(EventsDataSource.Event e : pendingEvents.values()) {
						EventsDataSource.getInstance().deleteEvent(e.eid, e.bid);
					}
					pendingEvents.clear();
		    		if(scrollView != null && ServersDataSource.getInstance().count() > 0)
						scrollView.setEnabled(true);
					if(cid != -1)
						messageTxt.setEnabled(true);
				} else {
		    		if(scrollView != null && ServersDataSource.getInstance().count() == 0)
						scrollView.setEnabled(false);
		    		messageTxt.setEnabled(false);
				}
				break;
			case NetworkConnection.EVENT_BANLIST:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.getString("channel").equalsIgnoreCase(name)) {
	            	Bundle args = new Bundle();
	            	args.putInt("cid", cid);
	            	args.putInt("bid", bid);
	            	args.putString("event", event.toString());
	            	BanListFragment banList = (BanListFragment)getSupportFragmentManager().findFragmentByTag("banlist");
	            	if(banList == null) {
	            		banList = new BanListFragment();
			        	banList.setArguments(args);
			        	banList.show(getSupportFragmentManager(), "banlist");
	            	} else {
			        	banList.setArguments(args);
	            	}
				}
	            break;
			case NetworkConnection.EVENT_BACKLOG_END:
		    	update_subtitle();
		    	if(cid == -1) {
		    		if(launchBid != -1 && !open_bid(launchBid))
		    			if(conn.getUserInfo() != null && !open_bid(conn.getUserInfo().last_selected_bid))
		    					if(!open_bid(BuffersDataSource.getInstance().firstBid())) {
		    						if(scrollView != null)
		    							scrollView.setEnabled(true);
		    						scrollView.scrollTo(0, 0);
		    					}
		    	}
		    	break;
			case NetworkConnection.EVENT_USERINFO:
		    	updateUsersListFragmentVisibility();
				invalidateOptionsMenu();
		        if(refreshUpIndicatorTask != null)
		        	refreshUpIndicatorTask.cancel(true);
		        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
		        refreshUpIndicatorTask.execute((Void)null);
		        if(launchBid == -1 && cid == -1)
		        	launchBid = conn.getUserInfo().last_selected_bid;
				break;
			case NetworkConnection.EVENT_STATUSCHANGED:
				try {
					event = (IRCCloudJSONObject)msg.obj;
					if(event.cid() == cid) {
						status = event.getString("new_status");
						invalidateOptionsMenu();
					}
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_MAKESERVER:
				ServersDataSource.Server server = (ServersDataSource.Server)msg.obj;
				if(server.cid == cid) {
					status = server.status;
					invalidateOptionsMenu();
				}
				break;
			case NetworkConnection.EVENT_MAKEBUFFER:
				BuffersDataSource.Buffer buffer = (BuffersDataSource.Buffer)msg.obj;
				if(bid == -1 && buffer.cid == cid && buffer.name.equalsIgnoreCase(name)) {
					Log.i("IRCCloud", "Got my new buffer id: " + buffer.bid);
					bid = buffer.bid;
			    	if(getSupportFragmentManager().findFragmentById(R.id.BuffersList) != null)
			    		((BuffersListFragment)getSupportFragmentManager().findFragmentById(R.id.BuffersList)).setSelectedBid(bid);
			    	Notifications.getInstance().excludeBid(bid);
			    	Notifications.getInstance().showNotifications(null);
				}
				break;
			case NetworkConnection.EVENT_BUFFERARCHIVED:
				event_bid = (Integer)msg.obj;
				if(event_bid == bid) {
					archived = 1;
					invalidateOptionsMenu();
					subtitle.setVisibility(View.VISIBLE);
					subtitle.setText("(archived)");
				}
				break;
			case NetworkConnection.EVENT_BUFFERUNARCHIVED:
				event_bid = (Integer)msg.obj;
				if(event_bid == bid) {
					archived = 0;
					invalidateOptionsMenu();
					subtitle.setVisibility(View.GONE);
				}
				break;
			case NetworkConnection.EVENT_JOIN:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.bid() == bid && event.type().equalsIgnoreCase("you_joined_channel")) {
					joined = 1;
					invalidateOptionsMenu();
				}
				break;
			case NetworkConnection.EVENT_PART:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.bid() == bid && event.type().equalsIgnoreCase("you_parted_channel")) {
					joined = 0;
					invalidateOptionsMenu();
				}
				break;
			case NetworkConnection.EVENT_CHANNELINIT:
				ChannelsDataSource.Channel channel = (ChannelsDataSource.Channel)msg.obj;
				if(channel.bid == bid) {
					joined = 1;
					archived = 0;
			    	update_subtitle();
					invalidateOptionsMenu();
				}
				break;
			case NetworkConnection.EVENT_CONNECTIONDELETED:
			case NetworkConnection.EVENT_DELETEBUFFER:
				Integer id = (Integer)msg.obj;
				if(id == ((msg.what==NetworkConnection.EVENT_CONNECTIONDELETED)?cid:bid)) {
		        	if(backStack != null && backStack.size() > 0) {
		        		Integer bid = backStack.get(0);
		        		backStack.remove(0);
		        		buffer = BuffersDataSource.getInstance().getBuffer(bid);
		    			onBufferSelected(buffer.cid, buffer.bid, buffer.name, buffer.last_seen_eid, buffer.min_eid, 
		    					buffer.type, 1, buffer.archived, status);
		        		backStack.remove(0);
		        	} else {
		        		if(!open_bid(BuffersDataSource.getInstance().firstBid()))
		        			finish();
		        	}
				}
				break;
			case NetworkConnection.EVENT_CHANNELTOPIC:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.bid() == bid) {
		        	try {
						if(event.getString("topic").length() > 0) {
							subtitle.setVisibility(View.VISIBLE);
							subtitle.setText(event.getString("topic"));
						} else {
							subtitle.setVisibility(View.GONE);
						}
					} catch (Exception e1) {
						subtitle.setVisibility(View.GONE);
						e1.printStackTrace();
					}
				}
				break;
			case NetworkConnection.EVENT_SELFBACK:
		    	try {
					event = (IRCCloudJSONObject)msg.obj;
					if(event.cid() == cid && event.getString("nick").equalsIgnoreCase(name)) {
			    		subtitle.setVisibility(View.GONE);
						subtitle.setText("");
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_AWAY:
		    	try {
					event = (IRCCloudJSONObject)msg.obj;
					if((event.bid() == bid || (event.type().equalsIgnoreCase("self_away") && event.cid() == cid)) && event.getString("nick").equalsIgnoreCase(name)) {
			    		subtitle.setVisibility(View.VISIBLE);
			    		if(event.has("away_msg"))
			    			subtitle.setText("Away: " + event.getString("away_msg"));
			    		else
			    			subtitle.setText("Away: " + event.getString("msg"));
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_HEARTBEATECHO:
		        if(refreshUpIndicatorTask != null)
		        	refreshUpIndicatorTask.cancel(true);
		        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
		        refreshUpIndicatorTask.execute((Void)null);
				break;
			case NetworkConnection.EVENT_FAILURE_MSG:
				event = (IRCCloudJSONObject)msg.obj;
				if(event.has("_reqid")) {
					int reqid = event.getInt("_reqid");
					if(pendingEvents.containsKey(reqid)) {
						EventsDataSource.Event e = pendingEvents.get(reqid);
						EventsDataSource.getInstance().deleteEvent(e.eid, e.bid);
						pendingEvents.remove(event.getInt("_reqid"));
						e.msg = ColorFormatter.irc_to_html(e.msg + " \u00034(FAILED)\u000f");
						conn.notifyHandlers(NetworkConnection.EVENT_BUFFERMSG, e);
					}
				}
				break;
			case NetworkConnection.EVENT_BUFFERMSG:
				try {
					EventsDataSource.Event e = (EventsDataSource.Event)msg.obj;
					if(e.bid != bid && upView != null) {
				        if(refreshUpIndicatorTask != null)
				        	refreshUpIndicatorTask.cancel(true);
				        refreshUpIndicatorTask = new RefreshUpIndicatorTask();
				        refreshUpIndicatorTask.execute((Void)null);
					}
					if(e.from.equalsIgnoreCase(name)) {
						for(EventsDataSource.Event e1 : pendingEvents.values()) {
							EventsDataSource.getInstance().deleteEvent(e1.eid, e1.bid);
						}
						pendingEvents.clear();
					} else if(pendingEvents.containsKey(e.reqid)) {
						e = pendingEvents.get(e.reqid);
						EventsDataSource.getInstance().deleteEvent(e.eid, e.bid);
						pendingEvents.remove(e.reqid);
					}
				} catch (Exception e1) {
				}
				break;
			}
		}
	};
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	if(type != null) {
	    	if(type.equalsIgnoreCase("channel")) {
	    		getSupportMenuInflater().inflate(R.menu.activity_message_channel_userlist, menu);
	    		getSupportMenuInflater().inflate(R.menu.activity_message_channel, menu);
	    	} else if(type.equalsIgnoreCase("conversation"))
	    		getSupportMenuInflater().inflate(R.menu.activity_message_conversation, menu);
	    	else if(type.equalsIgnoreCase("console"))
	    		getSupportMenuInflater().inflate(R.menu.activity_message_console, menu);
	
	    	getSupportMenuInflater().inflate(R.menu.activity_message_archive, menu);
    	}
    	getSupportMenuInflater().inflate(R.menu.activity_main, menu);

        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
    	if(type != null) {
        	if(archived == 0) {
        		menu.findItem(R.id.menu_archive).setTitle(R.string.menu_archive);
        	} else {
        		menu.findItem(R.id.menu_archive).setTitle(R.string.menu_unarchive);
        	}
	    	if(type.equalsIgnoreCase("channel")) {
	        	if(joined == 0) {
	        		menu.findItem(R.id.menu_leave).setTitle(R.string.menu_rejoin);
	        		menu.findItem(R.id.menu_archive).setVisible(true);
	        		menu.findItem(R.id.menu_archive).setEnabled(true);
	        		menu.findItem(R.id.menu_delete).setVisible(true);
	        		menu.findItem(R.id.menu_delete).setEnabled(true);
	        		if(menu.findItem(R.id.menu_userlist) != null) {
	        			menu.findItem(R.id.menu_userlist).setEnabled(false);
	        			menu.findItem(R.id.menu_userlist).setVisible(false);
	        		}
	        	} else {
	        		menu.findItem(R.id.menu_leave).setTitle(R.string.menu_leave);
	        		menu.findItem(R.id.menu_archive).setVisible(false);
	        		menu.findItem(R.id.menu_archive).setEnabled(false);
	        		menu.findItem(R.id.menu_delete).setVisible(false);
	        		menu.findItem(R.id.menu_delete).setEnabled(false);
	        		if(menu.findItem(R.id.menu_userlist) != null) {
		        		boolean hide = false;
		        		try {
		        			if(conn != null && conn.getUserInfo() != null && conn.getUserInfo().prefs != null) {
								JSONObject hiddenMap = conn.getUserInfo().prefs.getJSONObject("channel-hiddenMembers");
								if(hiddenMap.has(String.valueOf(bid)) && hiddenMap.getBoolean(String.valueOf(bid)))
									hide = true;
		        			}
						} catch (JSONException e) {
						}
						if(hide) {
			        		menu.findItem(R.id.menu_userlist).setEnabled(false);
			        		menu.findItem(R.id.menu_userlist).setVisible(false);
						} else {
			        		menu.findItem(R.id.menu_userlist).setEnabled(true);
			        		menu.findItem(R.id.menu_userlist).setVisible(true);
						}
	        		}
	        	}
	    	} else if(type.equalsIgnoreCase("console")) {
	    		menu.findItem(R.id.menu_archive).setVisible(false);
	    		menu.findItem(R.id.menu_archive).setEnabled(false);
	    		Log.i("IRCCloud", "Status: " + status);
	    		if(status != null && status.contains("connected") && !status.startsWith("dis")) {
	    			menu.findItem(R.id.menu_disconnect).setTitle(R.string.menu_disconnect);
	        		menu.findItem(R.id.menu_delete).setVisible(false);
	        		menu.findItem(R.id.menu_delete).setEnabled(false);
	    		} else {
	    			menu.findItem(R.id.menu_disconnect).setTitle(R.string.menu_reconnect);
	        		menu.findItem(R.id.menu_delete).setVisible(true);
	        		menu.findItem(R.id.menu_delete).setEnabled(true);
	    		}
	    	}
    	}
    	return super.onPrepareOptionsMenu(menu);
    }
    
    private OnClickListener upClickListener = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
        	if(scrollView != null) {
	        	if(scrollView.getScrollX() < buffersListView.getWidth() / 4) {
	        		scrollView.smoothScrollTo(buffersListView.getWidth(), 0);
	        		upView.setVisibility(View.VISIBLE);
	        	} else {
        			scrollView.smoothScrollTo(0, 0);
	        		upView.setVisibility(View.INVISIBLE);
	        	}
        	}
		}
    	
    };
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	AlertDialog.Builder builder;
    	AlertDialog dialog;
    	
        switch (item.getItemId()) {
	        case R.id.menu_add_network:
	        	EditConnectionFragment connFragment = new EditConnectionFragment();
	            connFragment.show(getSupportFragmentManager(), "dialog");
	            break;
	        case R.id.menu_channel_options:
	        	ChannelOptionsFragment newFragment = new ChannelOptionsFragment(cid, bid);
	            newFragment.show(getSupportFragmentManager(), "dialog");
	        	break;
            case R.id.menu_userlist:
            	if(scrollView != null) {
		        	if(scrollView.getScrollX() > buffersListView.getWidth()) {
	        			scrollView.smoothScrollTo(buffersListView.getWidth(), 0);
		        	} else {
		        		scrollView.smoothScrollTo(buffersListView.getWidth() + userListView.getWidth(), 0);
		        	}
            	}
            	return true;
            case R.id.menu_ignore_list:
            	Bundle args = new Bundle();
            	args.putInt("cid", cid);
	        	IgnoreListFragment ignoreList = new IgnoreListFragment();
	        	ignoreList.setArguments(args);
	            ignoreList.show(getSupportFragmentManager(), "ignorelist");
                return true;
            case R.id.menu_ban_list:
            	conn.mode(cid, name, "b");
                return true;
            case R.id.menu_leave:
            	if(joined == 0)
            		conn.join(cid, name, "");
            	else
            		conn.part(cid, name, "");
            	return true;
            case R.id.menu_archive:
            	if(archived == 0)
            		conn.archiveBuffer(cid, bid);
            	else
            		conn.unarchiveBuffer(cid, bid);
            	return true;
            case R.id.menu_delete:
            	builder = new AlertDialog.Builder(MessageActivity.this);
            	
            	if(type.equalsIgnoreCase("console"))
            		builder.setTitle("Delete Connection");
            	else
            		builder.setTitle("Delete History");
            	
            	if(type.equalsIgnoreCase("console"))
            		builder.setMessage("Are you sure you want to remove this connection?");
            	else if(type.equalsIgnoreCase("channel"))
            		builder.setMessage("Are you sure you want to clear your history in " + name + "?");
            	else
            		builder.setMessage("Are you sure you want to clear your history with " + name + "?");
            	
            	builder.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
            	});
            	builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
		            	if(type.equalsIgnoreCase("console")) {
		            		conn.deleteServer(cid);
		            	} else {
		                	conn.deleteBuffer(cid, bid);
		            	}
						dialog.dismiss();
					}
            	});
	    		dialog = builder.create();
	    		dialog.setOwnerActivity(MessageActivity.this);
	    		dialog.show();
            	return true;
            case R.id.menu_editconnection:
	        	EditConnectionFragment editFragment = new EditConnectionFragment();
	        	editFragment.setCid(cid);
	            editFragment.show(getSupportFragmentManager(), "editconnection");
            	return true;
            case R.id.menu_disconnect:
        		if(status != null && status.contains("connected") && !status.startsWith("dis")) {
        			conn.disconnect(cid, "");
        		} else {
        			conn.reconnect(cid);
        		}
        		return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    void editTopic() {
    	ChannelsDataSource.Channel c = ChannelsDataSource.getInstance().getChannelForBuffer(bid);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = getLayoutInflater();
    	View view = inflater.inflate(R.layout.dialog_textprompt,null);
    	TextView prompt = (TextView)view.findViewById(R.id.prompt);
    	final EditText input = (EditText)view.findViewById(R.id.textInput);
    	input.setText(c.topic_text);
    	prompt.setVisibility(View.GONE);
    	builder.setTitle("Channel Topic");
		builder.setView(view);
		builder.setPositiveButton("Set Topic", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				conn.say(cid, name, "/topic " + input.getText().toString());
				dialog.dismiss();
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.setOwnerActivity(this);
		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		dialog.show();
    }
    
	@Override
	public void onMessageDoubleClicked(EventsDataSource.Event event) {
		String from = event.from;
		if(from == null || from.length() == 0)
			from = event.nick;

		if(messageTxt == null || event == null || from == null || from.length() == 0)
			return;
		
		if(messageTxt.getText().length() == 0)
			messageTxt.append(from + ": ");
		else
			messageTxt.append(" " + from + " ");
	}
	
	@Override
	public boolean onMessageLongClicked(EventsDataSource.Event event) {
		String from = event.from;
		if(from == null || from.length() == 0)
			from = event.nick;

		UsersDataSource.User user;
		
		if(type.equals("channel"))
			user = UsersDataSource.getInstance().getUser(cid, name, from);
		else
			user = UsersDataSource.getInstance().getUser(cid, from);

		if(user == null && from != null && event.hostmask != null) {
			user = UsersDataSource.getInstance().new User();
			user.nick = from;
			user.hostmask = event.hostmask;
			user.mode = "";
		}
		
		if(user == null && event.html == null)
			return false;
		
		if(event.html != null)
			showUserPopup(user, ColorFormatter.html_to_spanned(event.html));
		else
			showUserPopup(user, null);
		return true;
    }
    
	@Override
	public void onUserSelected(int c, String chan, String nick) {
		UsersDataSource u = UsersDataSource.getInstance();
		if(type.equals("channel"))
			showUserPopup(u.getUser(cid, name, nick), null);
		else
			showUserPopup(u.getUser(cid, nick), null);
	}
	
	@SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
	private void showUserPopup(UsersDataSource.User user, Spanned message) {
		ArrayList<String> itemList = new ArrayList<String>();
   		final String[] items;
   		final Spanned text_to_copy = message;
		selected_user = user;
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		if(message != null)
			itemList.add("Copy Message");
		
		if(selected_user != null) {
			itemList.add("Open");
			itemList.add("Invite to a channel�");
			itemList.add("Ignore");
			if(type.equalsIgnoreCase("channel")) {
				UsersDataSource.User self_user = UsersDataSource.getInstance().getUser(cid, name, ServersDataSource.getInstance().getServer(cid).nick);
				if(self_user.mode.contains("q") || self_user.mode.contains("a") || self_user.mode.contains("o")) {
					if(selected_user.mode.contains("o"))
						itemList.add("Deop");
					else
						itemList.add("Op");
				}
				if(self_user.mode.contains("q") || self_user.mode.contains("a") || self_user.mode.contains("o") || self_user.mode.contains("h")) {
					itemList.add("Kick�");
					itemList.add("Ban�");
				}
			}
		}

		items = itemList.toArray(new String[itemList.size()]);
		
		if(selected_user != null)
			builder.setTitle(selected_user.nick + "\n(" + selected_user.hostmask + ")");
		else
			builder.setTitle("Message");
		
		builder.setItems(items, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialogInterface, int item) {
	    		AlertDialog.Builder builder = new AlertDialog.Builder(MessageActivity.this);
	    		LayoutInflater inflater = getLayoutInflater();
	    		ServersDataSource s = ServersDataSource.getInstance();
	    		ServersDataSource.Server server = s.getServer(cid);
	    		View view;
	    		final TextView prompt;
	    		final EditText input;
	    		AlertDialog dialog;

	    		if(items[item].equals("Copy Message")) {
	    			if(Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
						android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
	    			    clipboard.setText(text_to_copy);
	    			} else {
	    			    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); 
	    			    android.content.ClipData clip = android.content.ClipData.newPlainText("IRCCloud Message",text_to_copy);
	    			    clipboard.setPrimaryClip(clip);
	    			}
	    		} else if(items[item].equals("Open")) {
		    		BuffersDataSource b = BuffersDataSource.getInstance();
		    		BuffersDataSource.Buffer buffer = b.getBufferByName(cid, selected_user.nick);
		    		if(getSupportFragmentManager().findFragmentById(R.id.BuffersList) != null) {
			    		if(buffer != null) {
			    			onBufferSelected(buffer.cid, buffer.bid, buffer.name, buffer.last_seen_eid, buffer.min_eid, 
			    					buffer.type, 1, buffer.archived, status);
			    		} else {
			    			onBufferSelected(cid, -1, selected_user.nick, 0, 0, "conversation", 1, 0, status);
			    		}
		    		} else {
			    		Intent i = new Intent(MessageActivity.this, MessageActivity.class);
			    		if(buffer != null) {
				    		i.putExtra("cid", buffer.cid);
				    		i.putExtra("bid", buffer.bid);
				    		i.putExtra("name", buffer.name);
				    		i.putExtra("last_seen_eid", buffer.last_seen_eid);
				    		i.putExtra("min_eid", buffer.min_eid);
				    		i.putExtra("type", buffer.type);
				    		i.putExtra("joined", 1);
				    		i.putExtra("archived", buffer.archived);
				    		i.putExtra("status", status);
			    		} else {
				    		i.putExtra("cid", cid);
				    		i.putExtra("bid", -1);
				    		i.putExtra("name", selected_user.nick);
				    		i.putExtra("last_seen_eid", 0L);
				    		i.putExtra("min_eid", 0L);
				    		i.putExtra("type", "conversation");
				    		i.putExtra("joined", 1);
				    		i.putExtra("archived", 0);
				    		i.putExtra("status", status);
			    		}
			    		startActivity(i);
		    		}
	    		} else if(items[item].equals("Invite to a channel�")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	prompt.setText("Invite " + selected_user.nick + " to a channel");
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Invite", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.invite(cid, input.getText().toString(), selected_user.nick);
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		} else if(items[item].equals("Ignore")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	input.setText("*!"+selected_user.hostmask);
		        	prompt.setText("Ignore messages for " + selected_user.nick + " at this hostmask");
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Ignore", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.ignore(cid, input.getText().toString());
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		} else if(items[item].equals("Op")) {
	    			conn.mode(cid, name, "+o " + selected_user.nick);
	    		} else if(items[item].equals("Deop")) {
	    			conn.mode(cid, name, "-o " + selected_user.nick);
	    		} else if(items[item].equals("Kick�")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	prompt.setText("Give a reason for kicking");
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Kick", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.kick(cid, name, selected_user.nick, input.getText().toString());
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		} else if(items[item].equals("Ban�")) {
		        	view = inflater.inflate(R.layout.dialog_textprompt,null);
		        	prompt = (TextView)view.findViewById(R.id.prompt);
		        	input = (EditText)view.findViewById(R.id.textInput);
		        	input.setText("*!"+selected_user.hostmask);
		        	prompt.setText("Add a banmask for " + selected_user.nick);
		        	builder.setTitle(server.name + " (" + server.hostname + ":" + (server.port) + ")");
		    		builder.setView(view);
		    		builder.setPositiveButton("Ban", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							conn.mode(cid, name, "+b " + input.getText().toString());
							dialog.dismiss();
						}
		    		});
		    		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
		    		});
		    		dialog = builder.create();
		    		dialog.setOwnerActivity(MessageActivity.this);
		    		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		    		dialog.show();
	    		}
		    	dialogInterface.dismiss();
		    }
		});
		
		AlertDialog dialog = builder.create();
		dialog.setOwnerActivity(this);
		dialog.show();
    }

	@Override
	public void onBufferSelected(int cid, int bid, String name,
			long last_seen_eid, long min_eid, String type, int joined,
			int archived, String status) {
		if(scrollView != null) {
			if(buffersListView.getWidth() > 0)
				scrollView.smoothScrollTo(buffersListView.getWidth(), 0);
			upView.setVisibility(View.VISIBLE);
		}
		if(bid != this.bid) {
			if(bid != -1 && conn != null && conn.getUserInfo() != null)
				conn.getUserInfo().last_selected_bid = bid;
	    	for(int i = 0; i < backStack.size(); i++) {
	    		if(backStack.get(i) == this.bid)
	    			backStack.remove(i);
	    	}
	    	if(this.bid >= 0)
	    		backStack.add(0, this.bid);
			this.cid = cid;
			this.bid = bid;
			this.name = name;
			this.type = type;
			this.joined = joined;
			this.archived = archived;
			this.status = status;
	    	title.setText(name);
	    	getSupportActionBar().setTitle(name);
	    	update_subtitle();
	    	Bundle b = new Bundle();
	    	b.putInt("cid", cid);
	    	b.putInt("bid", bid);
	    	b.putLong("last_seen_eid", last_seen_eid);
	    	b.putLong("min_eid", min_eid);
	    	b.putString("name", name);
	    	b.putString("type", type);
	    	BuffersListFragment blf = (BuffersListFragment)getSupportFragmentManager().findFragmentById(R.id.BuffersList);
	    	MessageViewFragment mvf = (MessageViewFragment)getSupportFragmentManager().findFragmentById(R.id.messageViewFragment);
	    	UsersListFragment ulf = (UsersListFragment)getSupportFragmentManager().findFragmentById(R.id.usersListFragment);
	    	if(blf != null)
	    		blf.setSelectedBid(bid);
	    	if(mvf != null)
	    		mvf.setArguments(b);
	    	if(ulf != null)
	    		ulf.setArguments(b);
	
	    	AlphaAnimation anim = new AlphaAnimation(1, 0);
			anim.setDuration(200);
			anim.setFillAfter(true);
			mvf.getListView().startAnimation(anim);
			ulf.getListView().startAnimation(anim);
			shouldFadeIn = true;
	
	    	updateUsersListFragmentVisibility();
	    	invalidateOptionsMenu();
	    	Notifications.getInstance().excludeBid(bid);
	    	Notifications.getInstance().showNotifications(null);
		}
		if(cid != -1) {
			if(scrollView != null)
				scrollView.setEnabled(true);
			messageTxt.setEnabled(true);
		}
	}

	public void showUpButton(boolean show) {
		if(upView != null) {
			if(show) {
				upView.setVisibility(View.VISIBLE);
			} else {
				upView.setVisibility(View.INVISIBLE);
			}
		}
	}
	
	@Override
	public void onMessageViewReady() {
		if(shouldFadeIn) {
	    	MessageViewFragment mvf = (MessageViewFragment)getSupportFragmentManager().findFragmentById(R.id.messageViewFragment);
	    	UsersListFragment ulf = (UsersListFragment)getSupportFragmentManager().findFragmentById(R.id.usersListFragment);
	    	AlphaAnimation anim = new AlphaAnimation(0, 1);
			anim.setDuration(200);
			anim.setFillAfter(true);
			mvf.getListView().startAnimation(anim);
			ulf.getListView().startAnimation(anim);
			shouldFadeIn = false;
		}
	}
}
