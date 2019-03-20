package rs.ltt.android.ui.model;


import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import rs.ltt.android.Credentials;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.jmap.common.entity.EmailQuery;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;

public class MailboxQueryViewModel extends AbstractQueryViewModel {


    private final LiveData<MailboxOverviewItem> mailbox;

    private final LiveData<EmailQuery> emailQueryLiveData;

    MailboxQueryViewModel(final Application application, final String mailboxId) {
        super(application);
        LttrsDatabase lttrsDatabase = LttrsDatabase.getInstance(application, Credentials.username);
        if (mailboxId == null) {
            this.mailbox = lttrsDatabase.mailboxDao().getMailboxOverviewItem(Role.INBOX);
        } else {
            this.mailbox = lttrsDatabase.mailboxDao().getMailboxOverviewItem(mailboxId);
        }
        this.emailQueryLiveData = Transformations.map(mailbox, input -> {
            if (input == null) {
                return EmailQuery.unfiltered(true);
            } else {
                return EmailQuery.of(EmailFilterCondition.builder().inMailbox(input.id).build(), true);
            }
        });
        init();
    }



    public LiveData<MailboxOverviewItem> getMailbox() {
        return mailbox;
    }

    @Override
    protected LiveData<EmailQuery> getQuery() {
        return emailQueryLiveData;
    }
}