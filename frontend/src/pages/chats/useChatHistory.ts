import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { getChatMessages, getMessagesAround, markChatRead } from '../../api/chats';
import type { MessageData, MessagePageData, ReactionData } from '../../types/chat';

export interface ChatHistory {
  // oldest first, each message once
  messages: MessageData[];
  // the reactions to them, one per person and message (#217)
  reactions: ReactionData[];
  // older messages are still there to load
  hasMore: boolean;
  // after jumping to an old message the latest aren't loaded: scrolling down loads them, and new ones wait
  hasNewer: boolean;
  loading: boolean;
  loadingOlder: boolean;
  loadingNewer: boolean;
  // where the screen should go once this shows: a message by its id, or the bottom when there's no id.
  // seq changes every time, so going to the same message twice still moves
  focus?: { seq: number; id?: number };
  error?: unknown;
}

// a message can come twice (the send's answer and the live event), the list keeps one by id
function merge(current: MessageData[], more: MessageData[]) {
  const byId = new Map(current.map(message => [message.id, message]));
  more.forEach(message => byId.set(message.id, message));
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

// one per person and message: a newer one replaces theirs, and goes last so its pill doesn't jump ahead.
// A null emoji takes theirs off
function react(current: ReactionData[], more: ReactionData[]) {
  let next = current;
  more.forEach(reaction => {
    next = next.filter(r => r.messageId !== reaction.messageId || r.user.number !== reaction.user.number);
    if (reaction.emoji !== null) next = [...next, reaction];
  });
  return next;
}

const ignore = () => undefined;
// how far a catch-up goes after a reconnect, 50 messages a page
const CATCH_UP_PAGES = 20;
let lastFocus = 0;

// an open chat's messages: the 50 newest when it opens, 50 more each time loadOlder is called (#203).
// jumpTo loads the messages around an old one, and from there loadNewer goes back down to the latest (#219, #221).
// Opening it marks it as read. A chat that doesn't exist yet (a DM before its first message) has none to load
export function useChatHistory(chatId: number | null) {
  // something came while the tab was hidden: it's marked as read when you come back, not before you've seen it
  const unseen = useRef(false);
  const markRead = useCallback(() => {
    if (chatId === null) return;
    if (document.visibilityState === 'visible') markChatRead(chatId).catch(ignore);
    else unseen.current = true;
  }, [chatId]);

  useEffect(() => {
    const visible = () => {
      if (document.visibilityState !== 'visible' || !unseen.current) return;
      unseen.current = false;
      markRead();
    };
    document.addEventListener('visibilitychange', visible);
    return () => document.removeEventListener('visibilitychange', visible);
  }, [markRead]);

  const [history, setHistory] = useState<ChatHistory>(() => ({ messages: [], reactions: [], hasMore: false, hasNewer: false, loading: chatId !== null, loadingOlder: false, loadingNewer: false }));
  // what's on screen, for the loaders to read without being remade on every change.
  // set before the screen shows it, a scroll right after the messages appear was ignored
  const current = useRef(history);
  useLayoutEffect(() => { current.current = history; });
  // goes up whenever the messages on screen are swapped for others (a jump, back to the latest), so a page asked for
  // the old ones isn't put next to the new ones
  const view = useRef(0);

  // the messages on screen are swapped for this page
  const show = useCallback((page: MessagePageData, focus: ChatHistory['focus']) => setHistory(state => ({
    ...state, messages: merge([], page.messages), reactions: react([], page.reactions ?? []), hasMore: page.hasMore, hasNewer: page.hasNewer ?? false,
    loading: false, loadingOlder: false, loadingNewer: false, focus,
  })), []);

  useEffect(() => {
    if (chatId === null) return undefined;
    let open = true;
    getChatMessages(chatId).then(
      page => {
        if (!open) return;
        setHistory(state => ({ ...state, messages: merge(state.messages, page.messages), reactions: react(state.reactions, page.reactions ?? []), hasMore: page.hasMore, loading: false }));
        markRead();
      },
      error => { if (open) setHistory(state => ({ ...state, loading: false, error })); },
    );
    return () => { open = false; };
  }, [chatId, markRead]);

  const loadOlder = useCallback(() => {
    const { messages, hasMore, loading, loadingOlder } = current.current;
    if (chatId === null || !hasMore || loading || loadingOlder || !messages.length) return false;
    current.current = { ...current.current, loadingOlder: true };
    setHistory(state => ({ ...state, loadingOlder: true }));
    const asked = view.current;
    getChatMessages(chatId, { before: messages[0].id }).then(
      page => { if (asked === view.current) setHistory(state => ({ ...state, messages: merge(page.messages, state.messages), reactions: react(page.reactions ?? [], state.reactions), hasMore: page.hasMore, loadingOlder: false })); },
      // scrolling up again tries again
      () => { if (asked === view.current) setHistory(state => ({ ...state, loadingOlder: false })); },
    );
    return true;
  }, [chatId]);

  // scrolling down after a jump: the 50 after the newest one on screen, until the latest
  const loadNewer = useCallback(() => {
    const { messages, hasNewer, loading, loadingNewer } = current.current;
    if (chatId === null || !hasNewer || loading || loadingNewer || !messages.length) return false;
    current.current = { ...current.current, loadingNewer: true };
    setHistory(state => ({ ...state, loadingNewer: true }));
    const asked = view.current;
    getChatMessages(chatId, { after: messages[messages.length - 1].id }).then(
      page => {
        if (asked !== view.current) return;
        setHistory(state => ({ ...state, messages: merge(state.messages, page.messages), reactions: react(state.reactions, page.reactions ?? []), hasNewer: page.hasNewer ?? false, loadingNewer: false }));
        // the latest are on screen again
        if (!page.hasNewer) markRead();
      },
      () => { if (asked === view.current) setHistory(state => ({ ...state, loadingNewer: false })); },
    );
    return true;
  }, [chatId, markRead]);

  // go to a message: straight there if it's loaded, otherwise the messages around it replace the ones on screen.
  // Fails when it's gone (deleted meanwhile)
  const jumpTo = useCallback((id: number) => {
    if (chatId === null) return Promise.reject(new Error('No chat'));
    const focus = { seq: ++lastFocus, id };
    if (current.current.messages.some(message => message.id === id)) {
      setHistory(state => ({ ...state, focus }));
      return Promise.resolve();
    }
    const asked = ++view.current;
    return getMessagesAround(chatId, id).then(page => { if (asked === view.current) show(page, focus); });
  }, [chatId, show]);

  // back to the latest messages after a jump, at the bottom
  const toLatest = useCallback(() => {
    if (chatId === null || !current.current.hasNewer) return;
    const asked = ++view.current;
    // what's on screen is dropped straight away, so a message you send meanwhile isn't left out
    current.current = { ...current.current, messages: [], hasNewer: false, loading: true };
    setHistory(state => ({ ...state, messages: [], reactions: [], hasMore: false, hasNewer: false, loading: true, loadingOlder: false, loadingNewer: false }));
    getChatMessages(chatId).then(
      page => {
        if (asked !== view.current) return;
        setHistory(state => ({ ...state, messages: merge(state.messages, page.messages), reactions: react(state.reactions, page.reactions ?? []), hasMore: page.hasMore, loading: false, focus: { seq: ++lastFocus } }));
        markRead();
      },
      error => { if (asked === view.current) setHistory(state => ({ ...state, loading: false, error })); },
    );
  }, [chatId, markRead]);

  // after the connection drops and comes back (#317): the stretch on screen is asked for again, 50 at a time from the
  // oldest one shown, so new messages show and the ones edited or deleted meanwhile change or go. After a jump it stops
  // at the newest one shown, the latest still wait behind New messages
  const catchUp = useCallback(async () => {
    const { messages, loading, hasNewer } = current.current;
    if (chatId === null || loading) return;
    const asked = view.current;
    // nothing on screen (a new group chat, or every message deleted): what's there now is the latest page
    if (!messages.length) {
      let page: MessagePageData;
      try {
        page = await getChatMessages(chatId);
      } catch {
        return;
      }
      if (asked !== view.current) return;
      setHistory(state => ({ ...state, messages: merge(state.messages, page.messages), reactions: react(state.reactions, page.reactions ?? []), hasMore: page.hasMore }));
      if (page.messages.length) markRead();
      return;
    }
    const newestShown = messages[messages.length - 1].id;
    let after = messages[0].id - 1;
    let got = false;
    for (let pages = 0; pages < CATCH_UP_PAGES; pages++) {
      let page: MessagePageData;
      try {
        page = await getChatMessages(chatId, { after });
      } catch {
        return;
      }
      if (asked !== view.current) return;
      const more = page.hasNewer ?? false;
      const last = page.messages.length ? page.messages[page.messages.length - 1].id : after;
      // what's on screen between the last page and this one's end is swapped for the page. The last page also covers
      // up to the newest one shown before asking, a newer one has come live since
      const from = after;
      const to = more ? last : Math.max(last, newestShown);
      const inRange = (id: number) => id > from && id <= to;
      setHistory(state => ({
        ...state,
        messages: merge(state.messages.filter(m => !inRange(m.id)), page.messages),
        reactions: react(state.reactions.filter(r => !inRange(r.messageId)), page.reactions ?? []),
      }));
      got = got || page.messages.some(m => m.id > newestShown);
      after = last;
      if (!more || (hasNewer && last >= newestShown)) break;
    }
    if (got && !hasNewer) markRead();
  }, [chatId, markRead]);

  // the latest messages aren't on screen (after a jump), read as it is now, not as of the last render
  const behind = useCallback(() => current.current.hasNewer, []);
  // a new message, or the answer to one you sent. While the latest aren't loaded it waits for them
  const add = useCallback((message: MessageData) => setHistory(state => state.hasNewer ? state : { ...state, messages: merge(state.messages, [message]) }), []);
  // a newer version of a message (edited), and of the replies quoting it.
  // One that isn't loaded stays out, or it would show out of place above a gap
  const replace = useCallback((message: MessageData) => setHistory(state => ({
    ...state,
    messages: state.messages.map(m => {
      if (m.id === message.id) return message;
      if (m.replyTo?.id !== message.id) return m;
      return { ...m, replyTo: { ...m.replyTo, text: message.text, gifId: message.gifId, attachments: message.attachments } };
    }),
  })), []);
  // a deleted message: gone, and the replies to it say the original was deleted
  const remove = useCallback((id: number) => setHistory(state => ({
    ...state,
    messages: state.messages.filter(message => message.id !== id).map(m => m.replyTo?.id === id ? { ...m, replyTo: null } : m),
    reactions: state.reactions.filter(reaction => reaction.messageId !== id),
  })), []);
  // someone reacted, changed theirs or took it off: live, or the answer to yours. Only for a message on screen,
  // the others come with their page
  const applyReaction = useCallback((reaction: ReactionData) => setHistory(state => state.messages.some(m => m.id === reaction.messageId)
    ? { ...state, reactions: react(state.reactions, [reaction]) }
    : state), []);

  return { history, loadOlder, loadNewer, jumpTo, toLatest, behind, add, replace, remove, applyReaction, markRead, catchUp };
}
