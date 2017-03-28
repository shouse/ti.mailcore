/**
 * TiMailcore
 */

#import "TiMailcoreModule.h"
#import "TiBase.h"
#import "TiHost.h"
#import "TiUtils.h"
#import "MailCore/MailCore.h"

@implementation TiMailcoreModule

#pragma mark Internal

// this is generated for your module, please do not change it
-(id)moduleGUID
{
	return @"12eadb4c-788a-4eac-b2f5-0a1ead4ca2c7";
}

// this is generated for your module, please do not change it
-(NSString*)moduleId
{
	return @"ti.mailcore";
}

#pragma mark Lifecycle

-(void)startup
{
	// this method is called when the module is first loaded
	// you *must* call the superclass
	[super startup];

	NSLog(@"[INFO] %@ loaded",self);
}

-(void)shutdown:(id)sender
{
	// this method is called when the module is being unloaded
	// typically this is during shutdown. make sure you don't do too
	// much processing here or the app will be quit forceably

	// you *must* call the superclass
	[super shutdown:sender];
}

#pragma mark Cleanup

-(void)dealloc
{
	// release any resources that have been retained by the module
	[super dealloc];
}

#pragma mark Internal Memory Management

-(void)didReceiveMemoryWarning:(NSNotification*)notification
{
	// optionally release any resources that can be dynamically
	// reloaded once memory is available - such as caches
	[super didReceiveMemoryWarning:notification];
}

#pragma mark Listener Notifications

-(void)_listenerAdded:(NSString *)type count:(int)count
{
	if (count == 1 && [type isEqualToString:@"my_event"])
	{
		// the first (of potentially many) listener is being added
		// for event named 'my_event'
	}
}

-(void)_listenerRemoved:(NSString *)type count:(int)count
{
	if (count == 0 && [type isEqualToString:@"my_event"])
	{
		// the last listener called for event named 'my_event' has
		// been removed, we can optionally clean up any resources
		// since no body is listening at this point for that event
	}
}

- (id)_applyCredentials:(NSDictionary *)credentials smpt:(BOOL)smpt {
    NSString * email = [credentials objectForKey:@"email"];
    NSString * password = [credentials objectForKey:@"password"];
    NSString * host_name = [credentials objectForKey:@"host"];
    NSString * oauth_token = [credentials objectForKey:@"oauth_token"];
    
    int port_name = [TiUtils intValue:[credentials objectForKey:@"port"]];
    MCOConnectionType ctype = [credentials objectForKey:@"ctype"] ? [TiUtils intValue:[credentials objectForKey:@"ctype"]] : MCOConnectionTypeTLS;
    
    id session;
    if(smpt) {
        session = [[MCOSMTPSession alloc] init];
    } else {
        session = [[MCOIMAPSession alloc] init];
    }
    
    [session setUsername: email];
    [session setPassword: password];
    [session setHostname: host_name];
    [session setPort: port_name];
    [session setConnectionType: ctype];
    if(oauth_token) {
        [session setOAuth2Token: oauth_token];
    }
    
    if(smpt == NO) {
        MCOIMAPOperation * op = [session checkAccountOperation];
        [op start:^(NSError * error) {
            if(error) {
                NSLog(@"[INFO] Connection appears to be invalid.");
            }
        }];
    }
    return session;
}

- (NSMutableDictionary*)_messageToJSON:(MCOAbstractMessage *)msg {
    NSMutableDictionary * email = [self compose: nil];
    NSMutableDictionary * email_headers = [email valueForKey:@"headers"];
    NSMutableDictionary * email_addresses = [email valueForKey:@"addresses"];
    MCOMessageHeader * header = [msg header];
    
    if(header) {
        // Basic data and headers
        if(header.subject) {
            [email setObject:header.subject forKey:@"subject"];
        }
        if(header.date) {
            [email_headers setObject:[header.date description] forKey:@"date"];
        }
        if(header.receivedDate) {
            [email_headers setObject:[header.receivedDate description] forKey:@"received_date"];
        }
        if(header.allExtraHeadersNames) {
            for(NSString * extra in [header allExtraHeadersNames]) {
                NSString * extra_data = [header extraHeaderValueForName:extra];
                if(extra_data) {
                    [email_headers setObject:extra_data forKey:extra];
                }
            }
        }
        // 'From' address
        if(header.from) {
            NSMutableDictionary * from = [email_addresses valueForKey:@"from"];
            NSString * display_name = header.from.displayName;
            NSString * mailbox = header.from.mailbox;
            if(display_name) {
                [from setObject:display_name forKey:@"name"];
            }
            if(mailbox) {
                [from setObject:mailbox forKey:@"mailbox"];
            }
        }
        
        // Remainder of the address types
        NSDictionary * address_sections = @{
                                            @"to": header.to ? header.to : @[],
                                            @"cc": header.cc ? header.cc : @[],
                                            @"bcc": header.bcc ? header.bcc : @[],
                                            @"replyTo": header.replyTo ? header.replyTo : @[]
                                            };
        
        for(NSString * address_section in address_sections.allKeys) {
            NSArray * addresses = [address_sections valueForKey:address_section];
            
            if(addresses) {
                NSMutableArray * my_addresses = [[NSMutableArray alloc] init];
                for(MCOAddress * address in addresses) {
                    NSMutableDictionary * new_address = [[NSMutableDictionary alloc] init];
                    
                    NSString * display_name = address.displayName;
                    NSString * mailbox = address.mailbox;
                    if(display_name) {
                        [new_address setObject:display_name forKey:@"name"];
                    }
                    if(mailbox) {
                        [new_address setObject:mailbox forKey:@"mailbox"];
                    }
                    [my_addresses addObject:new_address];
                }
                [email_addresses setObject:my_addresses forKey:address_section];
            }
        }
    }
    
    return email;
}



- (NSMutableDictionary*)_getEmailStructure {
    return [@{
              @"subject": @"",
              @"body": @""
              } mutableCopy];
}

- (NSMutableDictionary*)_getHeaderStructure {
    return [@{
              } mutableCopy];
}

- (NSMutableDictionary*)_getAddressStructure {
    return [@{
              @"to": [[NSMutableArray alloc] init],
              @"cc": [[NSMutableArray alloc] init],
              @"bcc": [[NSMutableArray alloc] init],
              @"from": [[NSMutableDictionary alloc] init],
              @"replyTo": [[NSMutableDictionary alloc] init]
              } mutableCopy];
}

- (void)_applyHeader:(NSMutableDictionary*)header to:(NSMutableDictionary**)email {
    [*email setObject:header forKey:@"headers"];
}

- (void)_applyAddresses:(NSMutableDictionary*)address to:(NSMutableDictionary**)email {
    [*email setObject:address forKey:@"addresses"];
}


#pragma Public APIs

- (void)getFolders:(id)args {
    ENSURE_ARG_COUNT(args, 2);
    MCOIMAPSession * session = [self _applyCredentials:[args objectAtIndex:0] smpt: NO];
    
    MCOIMAPFetchFoldersOperation * op = [session fetchAllFoldersOperation];
    [op start:^(NSError * error, NSArray *folders) {
        if(error) {
            [[args objectAtIndex:1] call:@[[error description], @[]] thisObject:nil];
        } else {
            NSMutableArray * result = [[NSMutableArray alloc] init];
            for(MCOIMAPFolder * folder in folders) {
                [result addObject: folder.path];
            }
            [[args objectAtIndex:1] call:@[[NSNull null], result] thisObject:nil];
        }
    }];
}

- (void)getFolderInfo:(id)args {
    ENSURE_ARG_COUNT(args, 3);
    MCOIMAPSession * session = [self _applyCredentials:[args objectAtIndex:0] smpt: NO];
    
    MCOIMAPFolderInfoOperation * op = [session folderInfoOperation:[args objectAtIndex:1]];
    [op start:^(NSError * error, MCOIMAPFolderInfo * info) {
        if(error) {
            [[args objectAtIndex:1] call:@[[error description], @[]] thisObject:nil];
        } else {
            NSMutableDictionary * result = [[NSMutableDictionary alloc] init];
            
            [result setObject:[NSNumber numberWithUnsignedLong: [info uidNext]] forKey:@"UIDNEXT"];
            [result setObject:[NSNumber numberWithUnsignedLong: [info uidValidity]] forKey:@"UIDVALIDITY"];
            [result setObject:[NSNumber numberWithUnsignedLong: [info modSequenceValue]] forKey:@"HIGHESTMODSEQ"];
            [result setObject:[NSNumber numberWithInt: [info messageCount]] forKey:@"messages_count"];
            
            [[args objectAtIndex:2] call:@[[NSNull null], result] thisObject:nil];
        }
    }];
}



- (void)getMail:(id)args {
    ENSURE_ARG_COUNT(args, 4);
    MCOIMAPSession * session = [self _applyCredentials:[args objectAtIndex:0] smpt: NO];
    NSString * folder = [args objectAtIndex:1];
    NSArray * range = [args objectAtIndex:2];
    
    MCOIndexSet *uids;
    if(![range isEqual:[NSNull null]]) {
        uids = [MCOIndexSet indexSetWithRange:MCORangeMake([TiUtils intValue:range[0]], [TiUtils intValue:range[1]] - [TiUtils intValue:range[0]])];
    } else {
        uids = [MCOIndexSet indexSetWithRange:MCORangeMake(1, UINT64_MAX)];
    }
    
    MCOIMAPMessagesRequestKind requestKind = (MCOIMAPMessagesRequestKind)
        (MCOIMAPMessagesRequestKindHeaders | MCOIMAPMessagesRequestKindExtraHeaders | MCOIMAPMessagesRequestKindStructure | MCOIMAPMessagesRequestKindHeaderSubject);
    
    MCOIMAPFetchMessagesOperation *fetchOperation = [session fetchMessagesOperationWithFolder:folder requestKind:requestKind uids:uids];
    
	// TODO - extra headers should pass in
    fetchOperation.extraHeaders = @[@"Received-SPF"];
    [fetchOperation start:^(NSError * error, NSArray * fetchedMessages, MCOIndexSet * vanishedMessages) {
        if(error) {
            [[args objectAtIndex:3] call:@[[error description], @[]] thisObject:nil];
        } else {
            NSMutableArray * result = [[NSMutableArray alloc] init];
            for(MCOIMAPMessage * message in fetchedMessages) {
                NSMutableDictionary * email_result = [@{
                                                 @"uid": [NSNumber numberWithInt:message.uid],
                                                 @"sender_name": message.header.sender.displayName ? message.header.sender.displayName : @"",
                                                 @"sender_mailbox": message.header.sender.mailbox ? message.header.sender.mailbox : @"",
                                                 @"subject": message.header.subject ? message.header.subject : @"",
                                                 @"received_time": message.header.receivedDate ? [message.header.receivedDate description] : @"",
                                                 @"has_attachments": message.attachments && [message.attachments count] > 0 ? @true : @false
                                                 } mutableCopy];
                
                for(NSString * hname in message.header.allExtraHeadersNames) {
                    NSString * extra_header = [message.header extraHeaderValueForName:hname];
                    if(extra_header) {
                        [email_result setObject:extra_header forKey:hname];
                    }
                }
                
                [result addObject: email_result];
            }
            [[args objectAtIndex:3] call:@[[NSNull null], result] thisObject:nil];
        }
    }];
}

- (void)getMailInfo:(id)args {
    ENSURE_ARG_COUNT(args, 4);
    MCOIMAPSession * session = [self _applyCredentials:[args objectAtIndex:0] smpt: NO];
    NSString * folder = [args objectAtIndex:1];
    NSInteger * uid = [TiUtils intValue:[args objectAtIndex:2]];
    
    MCOIMAPFetchContentOperation * op = [session fetchMessageOperationWithFolder:folder uid: uid];
    
    [op start:^(NSError * error, NSData * data) {
        if(error) {
            [[args objectAtIndex:2] call:@[[error description], @{}] thisObject:nil];
        } else {
            MCOMessageParser * parser = [MCOMessageParser messageParserWithData:data];
            
            NSMutableDictionary * email = [self _messageToJSON: parser];
            if(parser) {
                [email setObject:[parser htmlBodyRendering] forKey:@"body"];
                
                if(parser.attachments) {
                    NSMutableArray * attachments = [[NSMutableArray alloc] init];
                    for(MCOAttachment * attachment in parser.attachments) {
                       
                        [attachments addObject: @{
                                                  @"file_name": attachment.filename,
                                                  @"mime_type": attachment.mimeType ? attachment.mimeType : @"",
                                                  @"data": attachment.data ? [attachment.data base64Encoding] : @""
                                                  }];
                    }
                    [email setObject: attachments forKey: @"attachments"];
                }
            }
            
            [[args objectAtIndex:3] call:@[[NSNull null], email] thisObject:nil];
        }
    }];
}

- (id)compose: (id)args {
    NSMutableDictionary * email_data = [self _getEmailStructure];
    NSMutableDictionary * email_header = [self _getHeaderStructure];
    NSMutableDictionary * email_address = [self _getAddressStructure];
    
    [self _applyHeader: email_header to:&email_data];
    [self _applyAddresses: email_address to:&email_data];
    
    return email_data;
}


- (id)send: (id)args {
    ENSURE_ARG_COUNT(args, 3);
    NSMutableDictionary * email = [args objectAtIndex:1];
    
    NSMutableDictionary * email_headers = [email valueForKey:@"headers"];
    NSMutableDictionary * email_addresses = [email valueForKey:@"addresses"];
    
    MCOMessageBuilder * builder = [[MCOMessageBuilder alloc] init];
    
    NSString * subject = [email objectForKey: @"subject"];
    NSInteger * receiver_count = 0;
    NSDictionary * from = [email_addresses objectForKey: @"from"];
    receiver_count += [[email_addresses objectForKey: @"to"] count];
    receiver_count += [[email_addresses objectForKey: @"cc"] count];
    receiver_count += [[email_addresses objectForKey: @"bcc"] count];
    if(subject && from && receiver_count > 0) {
        // Subject
        [[builder header] setSubject: subject];
        
        // From
        [[builder header] setFrom: [MCOAddress addressWithDisplayName:[from valueForKey:@"name"] mailbox: [from valueForKey:@"mailbox"]]];
        
        // To
        NSMutableArray * to = [[NSMutableArray alloc] init];
        NSArray * targets = [email_addresses objectForKey: @"to"];
        if(targets) {
            for(int i = 0; i < [targets count]; i++) {
                NSDictionary * target = [targets objectAtIndex: i];
                [to addObject:[MCOAddress addressWithDisplayName:[target valueForKey:@"name"] mailbox:[target valueForKey:@"mailbox"] ]];
            }
            [[builder header] setTo: to];
        }
        
        // cc
        NSMutableArray * cc = [[NSMutableArray alloc] init];
        targets = [email_addresses objectForKey: @"cc"];
        if(targets) {
            for(int i = 0; i < [targets count]; i++) {
                NSDictionary * target = [targets objectAtIndex: i];
                [cc addObject:[MCOAddress addressWithDisplayName:[target valueForKey:@"name"] mailbox:[target valueForKey:@"mailbox"] ]];
            }
            [[builder header] setCc: cc];
        }
        
        // bcc
        NSMutableArray * bcc = [[NSMutableArray alloc] init];
        targets = [email_addresses objectForKey: @"bcc"];
        if(targets) {
            for(int i = 0; i < [targets count]; i++) {
                NSDictionary * target = [targets objectAtIndex: i];
                [bcc addObject:[MCOAddress addressWithDisplayName:[target valueForKey:@"name"] mailbox:[target valueForKey:@"mailbox"] ]];
            }
            [[builder header] setBcc: bcc];
        }
        
        // Copy headers
        for(NSString * key in [email_headers allKeys]) {
            [[builder header] setExtraHeaderValue:[email_headers valueForKey:key] forName:key];
        }
        
        // Email body
        NSString * body = [email valueForKey:@"body"];
        if(body) {
            [builder setHTMLBody: body];
        }
        
        MCOSMTPSession* session = [self _applyCredentials:[args objectAtIndex:0] smpt: YES];
        MCOSMTPSendOperation * op = [session sendOperationWithData:[builder data]];
        [op start:^(NSError *error) {
            if(error) {
                [[args objectAtIndex:2] call:@[[error description], @[]] thisObject:nil];
            } else {
                [[args objectAtIndex:2] call:@[[NSNull null], [self _messageToJSON:builder]] thisObject:nil];
            }
        }];
        
    } else {
        NSLog(@"[ERROR] Cannot send email without a subject, a 'from' section, and some destination");
    }}

@end
